package com.pheeeew.pressrace.v2.infra;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.v2.domain.PressDayRead;
import com.pheeeew.pressrace.v2.domain.PressDirtyDay;
import com.pheeeew.pressrace.v2.domain.PressReadStatus;
import com.pheeeew.pressrace.v2.domain.PressScreenRange;
import com.pheeeew.pressrace.v2.domain.PressScreenRead;
import com.pheeeew.pressrace.v2.domain.PressStoredDay;
import com.pheeeew.pressrace.v2.domain.PressTodayParticipant;
import com.pheeeew.pressrace.v2.domain.PressTodayRead;
import com.pheeeew.pressrace.v2.domain.PressWriteResult;
import com.pheeeew.pressrace.v2.domain.PressWriteStatus;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.SetCondition;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Repository;

@ConditionalOnProperty(name = "pheeeew.press-race.write-mode", havingValue = "REDIS")
@Repository
public class PressRedisStore {

    private static final String PREFIX = "{press}:";
    private static final String DIRTY = PREFIX + "dirty";
    private static final String FLUSH_LEASE = PREFIX + "flush-lease";
    private static final String NICKNAME_VERSION = PREFIX + "nickname-version";
    private static final String NO_REQUEST = "";
    private static final long CAPACITY = 100;
    private static final long REFILL_PER_SECOND = 20;
    private static final long REQUEST_TTL_MILLIS = 600_000;
    private static final long NICKNAME_TTL_MILLIS = 600_000;
    private static final int TODAY_HEADER_SIZE = 7;
    private static final int TODAY_PARTICIPANT_SIZE = 5 + EmotionState.values().length;
    private static final int SCREEN_HEADER_SIZE = 9;
    private static final RedisScript<List> WRITE = script("redis/press-write.lua", List.class);
    private static final RedisScript<Long> CLEAR_DIRTY = script("redis/press-clear-dirty.lua", Long.class);
    private static final RedisScript<Long> SEED_DAY = script("redis/press-seed-day.lua", Long.class);
    private static final RedisScript<Long> RELEASE_LEASE = script("redis/press-release-lease.lua", Long.class);
    private static final RedisScript<List> READ_TODAY = script("redis/press-read-today.lua", List.class);
    private static final RedisScript<List> READ_SCREEN = script("redis/press-read-screen.lua", List.class);
    private static final RedisScript<List> READ_DAY = script("redis/press-read-day.lua", List.class);
    private static final RedisScript<Long> CHANGE_NICKNAME = script("redis/press-change-nickname.lua", Long.class);

    private final StringRedisTemplate redis;

    public PressRedisStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public PressWriteResult write(UUID devicePublicId, LocalDate date, Map<EmotionState, Integer> counts, UUID publicIdCandidate) {
        return write(devicePublicId, date, counts, publicIdCandidate, null, NO_REQUEST);
    }

    public PressWriteResult write(
            UUID devicePublicId,
            LocalDate date,
            Map<EmotionState, Integer> counts,
            UUID publicIdCandidate,
            UUID requestId,
            String requestHash
    ) {
        String device = devicePublicId.toString();
        List<String> keys = List.of(
                PREFIX + "bucket:" + device,
                deviceKey(date, devicePublicId),
                rankKey(date),
                totalKey(date),
                versionKey(date),
                DIRTY,
                PREFIX + "request:" + device + ":" + (requestId == null ? "none" : requestId),
                seededKey(date),
                activeKey(date)
        );
        List<String> arguments = new ArrayList<>(List.of(
                String.valueOf(date.toEpochDay()),
                date + ":" + device,
                device,
                publicIdCandidate.toString(),
                requestHash,
                String.valueOf(CAPACITY),
                String.valueOf(REFILL_PER_SECOND),
                String.valueOf(REQUEST_TTL_MILLIS)
        ));
        for (EmotionState state : EmotionState.values()) {
            arguments.add(state.name());
            arguments.add(String.valueOf(counts.getOrDefault(state, 0)));
        }
        List<?> reply = redis.execute(WRITE, keys, arguments.toArray());
        return toResult(reply, date);
    }

    public List<PressDirtyDay> findDirty() {
        Set<TypedTuple<String>> tuples = redis.opsForZSet().rangeWithScores(DIRTY, 0, -1);
        if (tuples == null) {
            return List.of();
        }
        return tuples.stream()
                .map(tuple -> PressDirtyDay.of(tuple.getValue(), tuple.getScore().longValue()))
                .toList();
    }

    public List<PressStoredDay> findDays(List<PressDirtyDay> dirtyDays) {
        List<Object> hashes = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (PressDirtyDay dirtyDay : dirtyDays) {
                connection.hashCommands().hGetAll(
                        deviceKey(dirtyDay.date(), dirtyDay.devicePublicId()).getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        List<PressStoredDay> days = new ArrayList<>();
        for (int index = 0; index < dirtyDays.size(); index++) {
            Map<?, ?> hash = (Map<?, ?>) hashes.get(index);
            if (hash != null && !hash.isEmpty()) {
                days.add(toStoredDay(dirtyDays.get(index), hash));
            }
        }
        return days;
    }

    public long clearDirty(Map<String, Long> versions) {
        if (versions.isEmpty()) {
            return 0;
        }
        List<String> arguments = new ArrayList<>();
        versions.forEach((member, version) -> {
            arguments.add(member);
            arguments.add(String.valueOf(version));
        });
        return redis.execute(CLEAR_DIRTY, List.of(DIRTY), arguments.toArray());
    }

    public long countDirty() {
        Long count = redis.opsForZSet().zCard(DIRTY);
        return count == null ? 0 : count;
    }

    public boolean seedDay(PressStoredDay day) {
        List<String> arguments = new ArrayList<>(List.of(
                day.publicId().toString(),
                String.valueOf(day.total()),
                String.valueOf(day.lastAppliedAt().toEpochMilli()),
                String.valueOf(day.version()),
                day.devicePublicId().toString()
        ));
        day.counts().forEach((state, count) -> {
            arguments.add(state.name());
            arguments.add(String.valueOf(count));
        });
        Long seeded = redis.execute(SEED_DAY,
                List.of(deviceKey(day.date(), day.devicePublicId()), rankKey(day.date()), totalKey(day.date()),
                        activeKey(day.date())),
                arguments.toArray());
        return seeded != null && seeded == 1;
    }

    public boolean isSeeded(LocalDate date) {
        return Boolean.TRUE.equals(redis.hasKey(seededKey(date)));
    }

    public void markSeeded(LocalDate date) {
        redis.opsForValue().set(seededKey(date), UUID.randomUUID().toString());
    }

    public PressTodayRead readToday(LocalDate date, String token, long version, long nicknameVersion) {
        List<String> keys = List.of(seededKey(date), versionKey(date), NICKNAME_VERSION, rankKey(date));
        List<String> arguments = new ArrayList<>(List.of(
                String.valueOf(date.toEpochDay()),
                token,
                String.valueOf(version),
                String.valueOf(nicknameVersion),
                PREFIX + "device:" + date + ":",
                PREFIX + "nickname:"
        ));
        arguments.addAll(stateNames());
        List<?> reply = redis.execute(READ_TODAY, keys, arguments.toArray());
        return toTodayRead(reply);
    }

    public PressScreenRead readScreen(LocalDate date, UUID devicePublicId, PressScreenRange range) {
        List<String> keys = List.of(seededKey(date), rankKey(date), activeKey(date), deviceKey(date, devicePublicId));
        List<String> arguments = new ArrayList<>(List.of(
                String.valueOf(date.toEpochDay()),
                devicePublicId.toString(),
                String.valueOf(range.topSize()),
                String.valueOf(range.nearbySize()),
                String.valueOf(range.leaderSize()),
                String.valueOf(range.activeWindow().toMillis()),
                PREFIX + "device:" + date + ":",
                PREFIX + "nickname:"
        ));
        arguments.addAll(stateNames());
        List<?> reply = redis.execute(READ_SCREEN, keys, arguments.toArray());
        return toScreenRead(reply);
    }

    public PressDayRead readDay(LocalDate date, UUID devicePublicId) {
        return readDay(date, List.of(seededKey(date), totalKey(date), deviceKey(date, devicePublicId)));
    }

    public PressDayRead readTotal(LocalDate date) {
        return readDay(date, List.of(seededKey(date), totalKey(date)));
    }

    public void changeNickname(UUID devicePublicId, String nickname) {
        redis.execute(CHANGE_NICKNAME, List.of(nicknameKey(devicePublicId), NICKNAME_VERSION),
                nickname == null ? "" : nickname, String.valueOf(NICKNAME_TTL_MILLIS));
    }

    public void fillNicknames(Map<UUID, String> nicknames) {
        if (nicknames.isEmpty()) {
            return;
        }
        redis.executePipelined((RedisCallback<Object>) connection -> {
            nicknames.forEach((devicePublicId, nickname) -> connection.stringCommands().set(
                    nicknameKey(devicePublicId).getBytes(StandardCharsets.UTF_8),
                    (nickname == null ? "" : nickname).getBytes(StandardCharsets.UTF_8),
                    SetCondition.ifAbsent(),
                    Expiration.milliseconds(NICKNAME_TTL_MILLIS)));
            return null;
        });
    }

    public boolean acquireFlushLease(String token, Duration timeToLive) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(FLUSH_LEASE, token, timeToLive));
    }

    public void releaseFlushLease(String token) {
        redis.execute(RELEASE_LEASE, List.of(FLUSH_LEASE), token);
    }

    private static <T> RedisScript<T> script(String path, Class<T> type) {
        return RedisScript.of(new ClassPathResource(path), type);
    }

    private String deviceKey(LocalDate date, UUID devicePublicId) {
        return PREFIX + "device:" + date + ":" + devicePublicId;
    }

    private String rankKey(LocalDate date) {
        return PREFIX + "rank:" + date;
    }

    private String activeKey(LocalDate date) {
        return PREFIX + "active:" + date;
    }

    private String totalKey(LocalDate date) {
        return PREFIX + "total:" + date;
    }

    private String seededKey(LocalDate date) {
        return PREFIX + "seeded:" + date;
    }

    private String versionKey(LocalDate date) {
        return PREFIX + "version:" + date;
    }

    private String nicknameKey(UUID devicePublicId) {
        return PREFIX + "nickname:" + devicePublicId;
    }

    private List<String> stateNames() {
        List<String> names = new ArrayList<>();
        for (EmotionState state : EmotionState.values()) {
            names.add(state.name());
        }
        return names;
    }

    private PressDayRead readDay(LocalDate date, List<String> keys) {
        List<String> arguments = new ArrayList<>(List.of(String.valueOf(date.toEpochDay())));
        arguments.addAll(stateNames());
        List<?> reply = redis.execute(READ_DAY, keys, arguments.toArray());
        return toDayRead(reply);
    }

    private PressWriteResult toResult(List<?> reply, LocalDate date) {
        PressWriteStatus status = PressWriteStatus.valueOf(reply.get(0).toString());
        if (status == PressWriteStatus.DATE_MISMATCH) {
            return PressWriteResult.of(status, LocalDate.ofEpochDay(((Number) reply.get(1)).longValue()), Map.of());
        }
        Map<EmotionState, Long> counts = new EnumMap<>(EmotionState.class);
        EmotionState[] states = EmotionState.values();
        for (int index = 0; index < states.length && index + 2 < reply.size(); index++) {
            counts.put(states[index], ((Number) reply.get(index + 2)).longValue());
        }
        return PressWriteResult.of(status, date, Map.copyOf(counts));
    }

    private PressStoredDay toStoredDay(PressDirtyDay dirtyDay, Map<?, ?> hash) {
        Map<EmotionState, Long> counts = new EnumMap<>(EmotionState.class);
        for (EmotionState state : EmotionState.values()) {
            Object count = hash.get(state.name());
            if (count != null) {
                counts.put(state, Long.parseLong(count.toString()));
            }
        }
        return PressStoredDay.of(
                dirtyDay.devicePublicId(),
                dirtyDay.date(),
                Map.copyOf(counts),
                Long.parseLong(hash.get("total").toString()),
                Instant.ofEpochMilli(Long.parseLong(hash.get("last_applied_at").toString())),
                UUID.fromString(hash.get("public_id").toString()),
                Long.parseLong(hash.get("version").toString())
        );
    }

    private PressTodayRead toTodayRead(List<?> reply) {
        PressReadStatus status = PressReadStatus.valueOf(reply.get(0).toString());
        Instant observedAt = Instant.ofEpochSecond(toLong(reply.get(1)), toLong(reply.get(2)) * 1_000);
        LocalDate date = LocalDate.ofEpochDay(toLong(reply.get(3)));
        if (status == PressReadStatus.DATE_MISMATCH || status == PressReadStatus.NOT_SEEDED) {
            return PressTodayRead.of(status, observedAt, date, null, 0, 0, List.of(), Set.of());
        }
        String token = reply.get(4).toString();
        long version = toLong(reply.get(5));
        long nicknameVersion = toLong(reply.get(6));
        List<PressTodayParticipant> participants = new ArrayList<>();
        Set<UUID> missingNicknames = new HashSet<>();
        for (int start = TODAY_HEADER_SIZE; start + TODAY_PARTICIPANT_SIZE <= reply.size(); start += TODAY_PARTICIPANT_SIZE) {
            PressTodayParticipant participant = toParticipant(reply.subList(start, start + TODAY_PARTICIPANT_SIZE));
            if (reply.get(start + TODAY_PARTICIPANT_SIZE - 1) == null) {
                missingNicknames.add(participant.devicePublicId());
            }
            participants.add(participant);
        }
        return PressTodayRead.of(status, observedAt, date, token, version, nicknameVersion, List.copyOf(participants),
                Set.copyOf(missingNicknames));
    }

    private PressScreenRead toScreenRead(List<?> reply) {
        PressReadStatus status = PressReadStatus.valueOf(reply.get(0).toString());
        Instant observedAt = Instant.ofEpochSecond(toLong(reply.get(1)), toLong(reply.get(2)) * 1_000);
        LocalDate date = LocalDate.ofEpochDay(toLong(reply.get(3)));
        if (status != PressReadStatus.READ) {
            return PressScreenRead.of(status, observedAt, date, 0, 0, 0, List.of(), List.of(), Set.of());
        }
        int nearbyCount = (int) toLong(reply.get(7));
        int topCount = (int) toLong(reply.get(8));
        Set<UUID> missingNicknames = new HashSet<>();
        int nearbyEnd = SCREEN_HEADER_SIZE + nearbyCount * TODAY_PARTICIPANT_SIZE;
        List<PressTodayParticipant> nearby = toParticipants(reply.subList(SCREEN_HEADER_SIZE, nearbyEnd),
                missingNicknames);
        List<PressTodayParticipant> top = toParticipants(
                reply.subList(nearbyEnd, nearbyEnd + topCount * TODAY_PARTICIPANT_SIZE), missingNicknames);
        return PressScreenRead.of(status, observedAt, date, (int) toLong(reply.get(4)), (int) toLong(reply.get(5)),
                toLong(reply.get(6)), nearby, top, Set.copyOf(missingNicknames));
    }

    private List<PressTodayParticipant> toParticipants(List<?> cells, Set<UUID> missingNicknames) {
        List<PressTodayParticipant> participants = new ArrayList<>();
        for (int start = 0; start + TODAY_PARTICIPANT_SIZE <= cells.size(); start += TODAY_PARTICIPANT_SIZE) {
            PressTodayParticipant participant = toParticipant(cells.subList(start, start + TODAY_PARTICIPANT_SIZE));
            if (cells.get(start + TODAY_PARTICIPANT_SIZE - 1) == null) {
                missingNicknames.add(participant.devicePublicId());
            }
            participants.add(participant);
        }
        return List.copyOf(participants);
    }

    private PressTodayParticipant toParticipant(List<?> cells) {
        Map<EmotionState, Long> counts = new EnumMap<>(EmotionState.class);
        EmotionState[] states = EmotionState.values();
        for (int index = 0; index < states.length; index++) {
            counts.put(states[index], toLong(cells.get(4 + index)));
        }
        Object nickname = cells.get(cells.size() - 1);
        return PressTodayParticipant.of(
                UUID.fromString(cells.get(0).toString()),
                UUID.fromString(cells.get(1).toString()),
                nickname == null || nickname.toString().isEmpty() ? null : nickname.toString(),
                toLong(cells.get(2)),
                Instant.ofEpochMilli(toLong(cells.get(3))),
                Map.copyOf(counts)
        );
    }

    private PressDayRead toDayRead(List<?> reply) {
        PressReadStatus status = PressReadStatus.valueOf(reply.get(0).toString());
        LocalDate date = LocalDate.ofEpochDay(toLong(reply.get(1)));
        if (status != PressReadStatus.READ) {
            return PressDayRead.of(status, date, 0, Map.of());
        }
        Map<EmotionState, Long> counts = new EnumMap<>(EmotionState.class);
        EmotionState[] states = EmotionState.values();
        for (int index = 0; index < states.length && index + 3 < reply.size(); index++) {
            counts.put(states[index], toLong(reply.get(index + 3)));
        }
        return PressDayRead.of(status, date, toLong(reply.get(2)), Map.copyOf(counts));
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
