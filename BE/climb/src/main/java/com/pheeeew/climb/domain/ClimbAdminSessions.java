package com.pheeeew.climb.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 관리자 키 확인과 관리자 세션. 세션은 서버 메모리에만 두어 서버가 다시 뜨면 사라진다.
 * 키가 비어 있으면 관리자 기능이 꺼진다.
 */
public class ClimbAdminSessions {

    public static final Duration TTL = Duration.ofHours(8);
    private static final int ATTEMPTS_PER_MINUTE = 5;

    private final byte[] keyDigest;
    private final ClimbRateLimit failures = new ClimbRateLimit(ATTEMPTS_PER_MINUTE, Duration.ofMinutes(1));
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public ClimbAdminSessions(String key) {
        this.keyDigest = key == null || key.isEmpty() ? null : digest(key);
    }

    public boolean enabled() {
        return keyDigest != null;
    }

    /** 맞으면 새 세션 토큰을 돌려준다. 틀린 시도만 client(IP)마다 분당 5번까지 센다. */
    public String open(String key, String client, Instant now) {
        if (!enabled()) {
            throw new ClimbException(ClimbErrorCode.ADMIN_DISABLED);
        }
        synchronized (failures) {
            if (failures.exhausted(client, now)) {
                throw new ClimbException(ClimbErrorCode.ADMIN_ATTEMPTS_LIMITED);
            }
            // 해시끼리 비교해 길이도 시간 차로 드러나지 않게 한다
            if (key == null || !MessageDigest.isEqual(digest(key), keyDigest)) {
                failures.tryAcquire(client, now);
                throw new ClimbException(ClimbErrorCode.ADMIN_KEY_MISMATCH);
            }
        }
        sessions.values().removeIf(expiresAt -> !now.isBefore(expiresAt));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, now.plus(TTL));
        return token;
    }

    public boolean valid(String token, Instant now) {
        Instant expiresAt = token == null ? null : sessions.get(token);
        return expiresAt != null && now.isBefore(expiresAt);
    }

    public void close(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
