package com.pheeeew.pressrace.application;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.PressCounts;
import com.pheeeew.emotion.domain.repository.DeviceDailyPressRepository;
import com.pheeeew.pressrace.domain.PendingPress;
import com.pheeeew.pressrace.domain.PressConnection;
import com.pheeeew.pressrace.domain.PressInputResult;
import com.pheeeew.pressrace.domain.PressSessionResult;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.pressrace.infra.PressRaceRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 접수는 독립 커밋하고 점수·최종 결과는 다음 트랜잭션에서 함께 확정한다.
 * ADR-0004의 단일 저장 예외를 확대하지 않고 승인된 #919의 다중 저장 계약을 따른다.
 * 서버 제어 행은 FOR SHARE로 보호하고, device → input → 감정명 순으로 배타 잠근다.
 * 다른 기기는 병렬 저장하며 서버 전환 UPDATE만 진행 중 트랜잭션을 기다린다. 송신은 반환 후 호출자가 한다.
 */
@Service
public class PressRaceService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final PressRaceRepository repository;
    private final DeviceDailyPressRepository presses;
    private final TransactionTemplate transaction;
    private final UUID serverGeneration;
    private final PressAdmission admission;

    @Autowired
    public PressRaceService(PressRaceRepository repository, DeviceDailyPressRepository presses,
            PlatformTransactionManager manager,
            @Value("${pheeeew.press-race.server-generation:}") String configuredGeneration,
            @Value("${pheeeew.press-race.admission-file:}") String admissionFile,
            @Value("${pheeeew.press-race.admission-required:false}") boolean admissionRequired) {
        this.repository = repository;
        this.presses = presses;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.serverGeneration = configuredGeneration.isBlank() ? UUID.randomUUID() : UUID.fromString(configuredGeneration);
        this.admission = new PressAdmission(admissionFile, serverGeneration, admissionRequired);
    }

    public PressRaceService(PressRaceRepository repository, DeviceDailyPressRepository presses,
            PlatformTransactionManager manager, String configuredGeneration) {
        this(repository, presses, manager, configuredGeneration, "", false);
    }

    public UUID serverGeneration() {
        return serverGeneration;
    }

    public boolean admissionClosed() {
        return admission.isClosed();
    }

    public String admissionRevision() {
        return admission.openRevision();
    }

    public void requireAdmissionRevision(String revision) {
        admission.requireOpen(revision);
    }

    public void acknowledgeAdmissionClosed() {
        admission.acknowledgeClosed();
    }

    public void connect(PressConnection connection) {
        connect(connection, admission.openRevision());
    }

    public void connect(PressConnection connection, String revision) {
        connect(connection, false, revision);
    }

    public void connectRecovery(PressConnection connection) {
        connectRecovery(connection, admission.openRevision());
    }

    public void connectRecovery(PressConnection connection, String revision) {
        connect(connection, true, revision);
    }

    public PressSessionResult initialize(PressConnection connection, List<PendingPress> pending) {
        return initialize(connection, pending, false);
    }

    public PressSessionResult initializeRecovery(PressConnection connection, List<PendingPress> pending) {
        return initialize(connection, pending, true);
    }

    public PressInputResult submit(PressConnection connection, UUID requestId, Map<EmotionState, Integer> counts) {
        PressInputResult received = receive(connection, requestId, counts);
        if (!"PENDING".equals(received.status())) {
            return received;
        }
        return apply(connection, requestId, counts);
    }

    public PressInputResult receive(PressConnection connection, UUID requestId, Map<EmotionState, Integer> counts) {
        String normalized = normalize(counts);
        requireRequestId(requestId);
        return inTransaction(revision -> {
            Map<String, Object> control = lockServerFence(revision);
            long deviceId = repository.deviceId(connection.devicePublicId());
            Map<String, Object> device = repository.lockDevice(deviceId);
            Map<String, Object> existing = repository.input(deviceId, requestId).orElse(null);
            Instant now = repository.now();
            if (existing != null) {
                checkReadable(control, device, connection, now);
                return result(requestId, normalized, existing);
            }
            checkConnection(control, device, connection, true, now);
            repository.receive(deviceId, requestId, normalized, hash(normalized), connection.connectionGeneration(), now);
            return result(requestId, normalized, repository.input(deviceId, requestId).orElseThrow());
        });
    }

    public PressInputResult apply(PressConnection connection, UUID requestId, Map<EmotionState, Integer> counts) {
        String normalized = normalize(counts);
        requireRequestId(requestId);
        return inTransaction(revision -> {
            Map<String, Object> control = lockServerFence(revision);
            long deviceId = repository.deviceId(connection.devicePublicId());
            Map<String, Object> device = repository.lockDevice(deviceId);
            Map<String, Object> row = repository.input(deviceId, requestId).orElse(null);
            Instant now = repository.now();
            checkReadable(control, device, connection, now);
            if (row == null) {
                return unknown(requestId, "NOT_FOUND");
            }
            if (!normalized.equals(row.get("normalized_counts")) || !"PENDING".equals(row.get("status"))) {
                return result(requestId, normalized, row);
            }
            String rejection = rejection(control, device, connection, row, counts, now);
            if (rejection == null && !repository.consume(deviceId, device, total(counts), now)) {
                rejection = "RATE_LIMITED";
            }
            if (rejection != null) {
                repository.reject(deviceId, requestId, rejection, now);
            } else {
                LocalDate date = date(now);
                increase(deviceId, date, counts, now);
                repository.applied(deviceId, requestId, date, now);
            }
            return result(requestId, normalized, repository.input(deviceId, requestId).orElseThrow());
        });
    }

    public List<PressInputResult> status(PressConnection connection, List<UUID> requestIds) {
        if (requestIds == null || requestIds.size() > 20 || requestIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new PressRaceException("INVALID_INPUT");
        }
        return inTransaction(revision -> {
            Map<String, Object> control = lockServerFence(revision);
            long deviceId = repository.deviceId(connection.devicePublicId());
            Map<String, Object> device = repository.lockDevice(deviceId);
            checkReadable(control, device, connection, repository.now());
            return requestIds.stream().map(id -> repository.input(deviceId, id)
                    .map(row -> result(id, row.get("normalized_counts").toString(), row))
                    .orElseGet(() -> unknown(id, "NOT_FOUND"))).toList();
        });
    }

    public Map<EmotionState, Long> pressHttp(UUID devicePublicId, PressCounts counts) {
        return inTransaction(revision -> {
            Map<String, Object> control = lockServerFence(revision);
            long deviceId = repository.deviceId(devicePublicId);
            Map<String, Object> device = repository.lockDevice(deviceId);
            Instant now = repository.now();
            LocalDate today = date(now);
            if (counts.appliedTotal() > 0) {
                checkServer(control, serverGeneration);
                if (!repository.consume(deviceId, device, counts.appliedTotal(), now)) {
                    throw new PressRaceException("RATE_LIMITED", 5);
                }
                increase(deviceId, today, counts.presses(), now);
            }
            return repository.counts(deviceId, today);
        });
    }

    private void connect(PressConnection connection, boolean recoveryOnly, String revision) {
        inTransaction(revision, permit -> {
            Map<String, Object> control = lockServerFence(permit);
            long deviceId = repository.deviceId(connection.devicePublicId());
            repository.lockDevice(deviceId);
            Instant now = repository.now();
            checkServer(control, connection.serverGeneration());
            checkToken(connection, now);
            if (!recoveryOnly) {
                checkEnabled(control, now);
            }
            repository.connection(deviceId, connection.connectionGeneration(), false);
            return null;
        });
    }

    private PressSessionResult initialize(PressConnection connection, List<PendingPress> pending, boolean recoveryOnly) {
        if (pending == null || pending.size() > 20) {
            throw new PressRaceException("INVALID_INPUT");
        }
        return inTransaction(revision -> {
            Map<String, Object> control = lockServerFence(revision);
            long deviceId = repository.deviceId(connection.devicePublicId());
            Map<String, Object> device = repository.lockDevice(deviceId);
            Instant now = repository.now();
            checkReadable(control, device, connection, now);
            if (!recoveryOnly) {
                checkEnabled(control, now);
            }
            repository.recover(deviceId, connection.connectionGeneration(), now);
            List<PressInputResult> results = new ArrayList<>();
            for (PendingPress input : pending) {
                if (input.requestId() == null || input.connectionGeneration() == null
                        || input.connectionGeneration().equals(connection.connectionGeneration())) {
                    throw new PressRaceException("INVALID_INPUT");
                }
                String normalized = normalize(input.counts());
                Map<String, Object> row = repository.input(deviceId, input.requestId()).orElse(null);
                if (row == null) {
                    repository.tombstone(deviceId, input.requestId(), normalized, hash(normalized),
                            connection.connectionGeneration(), now);
                    row = repository.input(deviceId, input.requestId()).orElseThrow();
                }
                results.add(result(input.requestId(), normalized, row));
            }
            repository.connection(deviceId, connection.connectionGeneration(), !recoveryOnly);
            LocalDate date = date(now);
            return PressSessionResult.of(repository.counts(deviceId, date), List.copyOf(results), date);
        });
    }

    private void increase(long deviceId, LocalDate date, Map<EmotionState, Integer> counts, Instant now) {
        counts.entrySet().stream().filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(EmotionState::name)))
                .forEach(entry -> presses.increase(date, entry.getKey().name(), deviceId, entry.getValue(), now));
        repository.project(deviceId, date, now);
    }

    private String rejection(Map<String, Object> control, Map<String, Object> device, PressConnection connection,
            Map<String, Object> row, Map<EmotionState, Integer> counts, Instant now) {
        if (!connection.connectionGeneration().equals(device.get("connection_generation"))
                || !connection.connectionGeneration().equals(row.get("original_generation"))
                || !Boolean.TRUE.equals(device.get("ready"))) {
            return "CONNECTION_REPLACED";
        }
        if (!enabled(control, now)) {
            return "RACE_DISABLED";
        }
        if (!now.isBefore(PressRaceRepository.instant(row.get("first_received_at")).plusSeconds(10))) {
            return "INPUT_EXPIRED";
        }
        if (counts.values().stream().anyMatch(value -> value < 0) || totalLong(counts) < 1) {
            return "INVALID_INPUT";
        }
        if (totalLong(counts) > 100) {
            return "INPUT_TOO_LARGE";
        }
        return null;
    }

    private void checkReadable(Map<String, Object> control, Map<String, Object> device,
            PressConnection connection, Instant now) {
        checkServer(control, connection.serverGeneration());
        checkToken(connection, now);
        if (!connection.connectionGeneration().equals(device.get("connection_generation"))) {
            throw new PressRaceException("CONNECTION_REPLACED");
        }
    }

    private void checkConnection(Map<String, Object> control, Map<String, Object> device,
            PressConnection connection, boolean requireReady, Instant now) {
        checkReadable(control, device, connection, now);
        checkEnabled(control, now);
        if (requireReady && !Boolean.TRUE.equals(device.get("ready"))) {
            throw new PressRaceException("SESSION_NOT_READY");
        }
    }

    private void checkServer(Map<String, Object> control, UUID generation) {
        if (!serverGeneration.equals(generation) || !serverGeneration.equals(control.get("active_server_generation"))) {
            throw new PressRaceException("SERVER_REPLACED");
        }
    }

    private void checkToken(PressConnection connection, Instant now) {
        if (connection.expiresAt() == null || !now.isBefore(connection.expiresAt())) {
            throw new PressRaceException("AUTH_EXPIRED");
        }
    }

    private void checkEnabled(Map<String, Object> control, Instant now) {
        if (!enabled(control, now)) {
            throw new PressRaceException("RACE_DISABLED");
        }
    }

    private boolean enabled(Map<String, Object> control, Instant now) {
        Object activation = control.get("activation_date");
        return Boolean.TRUE.equals(control.get("enabled")) && activation != null
                && !date(now).isBefore(LocalDate.parse(activation.toString()));
    }

    private PressInputResult result(UUID requestId, String normalized, Map<String, Object> row) {
        if (!normalized.equals(row.get("normalized_counts"))) {
            throw new PressRaceException("INPUT_CONFLICT", requestId);
        }
        String status = row.get("status").toString();
        boolean applied = "APPLIED".equals(status);
        return PressInputResult.of(requestId, status, (String) row.get("rejection_code"),
                applied ? LocalDate.parse(row.get("applied_date").toString()) : null,
                applied ? parseCounts(normalized) : null);
    }

    private PressInputResult unknown(UUID requestId, String status) {
        return PressInputResult.of(requestId, status, null, null, null);
    }

    private String normalize(Map<EmotionState, Integer> counts) {
        if (counts == null || counts.keySet().stream().anyMatch(java.util.Objects::isNull)
                || counts.values().stream().anyMatch(java.util.Objects::isNull)) {
            throw new PressRaceException("INVALID_INPUT");
        }
        return java.util.Arrays.stream(EmotionState.values()).sorted(Comparator.comparing(EmotionState::name))
                .map(state -> state.name() + "=" + counts.getOrDefault(state, 0))
                .collect(java.util.stream.Collectors.joining(","));
    }

    private Map<EmotionState, Integer> parseCounts(String normalized) {
        Map<EmotionState, Integer> counts = new TreeMap<>();
        for (String pair : normalized.split(",")) {
            String[] parts = pair.split("=");
            int value = Integer.parseInt(parts[1]);
            if (value > 0) {
                counts.put(EmotionState.valueOf(parts[0]), value);
            }
        }
        return Map.copyOf(counts);
    }

    private String hash(String normalized) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void requireRequestId(UUID requestId) {
        if (requestId == null) {
            throw new PressRaceException("INVALID_INPUT");
        }
    }

    private int total(Map<EmotionState, Integer> counts) {
        return Math.toIntExact(totalLong(counts));
    }

    private long totalLong(Map<EmotionState, Integer> counts) {
        return counts.values().stream().mapToLong(Integer::longValue).sum();
    }

    private LocalDate date(Instant now) {
        return LocalDate.ofInstant(now, KST);
    }

    private Map<String, Object> lockServerFence(String revision) {
        Map<String, Object> control = repository.lockServerFence();
        admission.requireOpen(revision);
        return control;
    }

    private <T> T inTransaction(Function<String, T> operation) {
        String revision = admission.openRevision();
        return inTransaction(revision, operation);
    }

    private <T> T inTransaction(String revision, Function<String, T> operation) {
        admission.requireOpen(revision);
        return transaction.execute(status -> operation.apply(revision));
    }
}
