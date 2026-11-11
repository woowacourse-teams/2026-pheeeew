package com.pheeeew.pressevent.application;

import static com.pheeeew.pressevent.exception.PressEventErrorCode.INVALID_CURSOR;

import com.pheeeew.common.exception.PheeeewException;
import com.pheeeew.pressevent.application.PressEventRankingResult.Standing;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public record PressEventCursor(int score, Instant achievedAt, UUID publicId) {

    private static final int MAX_LENGTH = 160;
    private static final Instant MAX_ACHIEVED_AT = Instant.parse("9999-12-31T23:59:59.999999Z");

    public static String encode(UUID eventId, Standing standing) {
        String payload = String.join("|", eventId.toString(), Integer.toString(standing.bestScore()),
                standing.achievedAt().toString(), standing.publicId().toString());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static PressEventCursor decode(UUID eventId, String encoded) {
        if (encoded == null) {
            return null;
        }
        if (encoded.length() > MAX_LENGTH) {
            throw new PheeeewException(INVALID_CURSOR, null);
        }

        try {
            String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] fields = payload.split("\\|", -1);
            if (fields.length != 4 || !eventId.toString().equals(fields[0])) {
                throw new IllegalArgumentException();
            }
            int score = Integer.parseInt(fields[1]);
            Instant achievedAt = Instant.parse(fields[2]);
            if (score < 0 || achievedAt.isBefore(Instant.EPOCH) || achievedAt.isAfter(MAX_ACHIEVED_AT)) {
                throw new IllegalArgumentException();
            }
            return new PressEventCursor(score, achievedAt, UUID.fromString(fields[3]));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new PheeeewException(INVALID_CURSOR, exception);
        }
    }
}
