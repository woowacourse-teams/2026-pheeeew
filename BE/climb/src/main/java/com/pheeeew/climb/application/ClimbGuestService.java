package com.pheeeew.climb.application;

import com.pheeeew.climb.domain.ClimbErrorCode;
import com.pheeeew.climb.domain.ClimbException;
import com.pheeeew.climb.domain.ClimbGuest;
import com.pheeeew.climb.domain.ClimbNickname;
import com.pheeeew.climb.domain.ClimbRateLimit;
import com.pheeeew.climb.domain.ClimbRunRecord;
import com.pheeeew.climb.infra.ClimbGuestRepository;
import com.pheeeew.climb.infra.ClimbRunRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ClimbGuestService {

    public static final Duration COOKIE_MAX_AGE = Duration.ofDays(180);
    private static final Duration LAST_SEEN_PRECISION = Duration.ofHours(1);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SecureRandom random = new SecureRandom();
    private final ClimbGuestRepository guests;
    private final ClimbRunRepository runs;
    private final ClimbRankingService ranking;
    private final ClimbRateLimit createLimit;
    // shortcut: 하루 변경 횟수를 메모리에만 센다. 배포로 서버가 다시 뜨면 그날 횟수가 초기화된다.
    private final ClimbRateLimit renameLimit = new ClimbRateLimit(3, Duration.ofDays(1));

    public ClimbGuestService(ClimbGuestRepository guests, ClimbRunRepository runs, ClimbRankingService ranking,
            int createsPerHour) {
        this.guests = guests;
        this.runs = runs;
        this.ranking = ranking;
        this.createLimit = new ClimbRateLimit(createsPerHour, Duration.ofHours(1));
    }

    public Created create(String rawNickname, String clientAddress) {
        String nickname = ClimbNickname.validate(rawNickname);
        Instant now = Instant.now();
        if (!createLimit.tryAcquire(clientAddress, now)) {
            throw new ClimbException(ClimbErrorCode.GUEST_CREATE_LIMITED);
        }
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        byte[] tagBytes = new byte[2];
        random.nextBytes(tagBytes);
        ClimbGuest guest = ClimbGuest.of(UUID.randomUUID(), HexFormat.of().formatHex(tagBytes), nickname, now, now);
        guests.save(guest, hash(token));
        return new Created(guest, token);
    }

    public Optional<ClimbGuest> authenticate(String token) {
        if (token == null || token.isEmpty() || token.length() > 64) {
            return Optional.empty();
        }
        Optional<ClimbGuest> guest = guests.findByTokenHash(hash(token));
        Instant now = Instant.now();
        guest.filter(found -> found.lastSeenAt().plus(LAST_SEEN_PRECISION).isBefore(now))
                .ifPresent(found -> guests.updateLastSeenAt(found.id(), now));
        return guest;
    }

    public ClimbGuest rename(ClimbGuest guest, String rawNickname) {
        String nickname = ClimbNickname.validate(rawNickname);
        Instant now = Instant.now();
        if (!renameLimit.tryAcquire(guest.id() + "/" + LocalDate.ofInstant(now, KST), now)) {
            throw new ClimbException(ClimbErrorCode.NICKNAME_CHANGE_LIMITED);
        }
        guests.updateNickname(guest.id(), nickname);
        ranking.rename(guest.id(), nickname);
        return ClimbGuest.of(guest.id(), guest.tag(), nickname, guest.createdAt(), guest.lastSeenAt());
    }

    public Optional<ClimbRunRecord> findLatestRun(UUID guestId) {
        return runs.findLatestUnfinished(guestId);
    }

    /** 180일 동안 접속하지 않은 손님과 그 기록을 지운다. */
    public void deleteInactive() {
        List<UUID> deleted = guests.deleteInactive(Instant.now().minus(COOKIE_MAX_AGE));
        if (!deleted.isEmpty()) {
            ranking.forget(deleted);
        }
    }

    private static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record Created(ClimbGuest guest, String token) {
    }
}
