package com.pheeeew.emotion.application;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_INVALID_CURSOR;

import com.pheeeew.emotion.application.dto.EmotionListCursor;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.exception.EmotionException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

public final class EmotionListCursorCodec {

    private static final String WITHIN_BOUNDS_VERSION = "1";
    private static final String WITHOUT_BOUNDS_VERSION = "3";
    private static final String FIELD_DELIMITER = "|";
    private static final int WITHIN_BOUNDS_FIELD_COUNT = 8;
    private static final int WITHOUT_BOUNDS_FIELD_COUNT = 5;
    private static final int MAX_CURSOR_LENGTH = 2_048;

    private EmotionListCursorCodec() {
    }

    public static EmotionListCursor decodeWithinBounds(String encoded) {
        try {
            String[] fields = decodeFields(encoded);
            boolean groupCursor = fields.length == WITHIN_BOUNDS_FIELD_COUNT + 1 && "2".equals(fields[0]);
            if (!groupCursor && (fields.length != WITHIN_BOUNDS_FIELD_COUNT || !WITHIN_BOUNDS_VERSION.equals(fields[0]))) {
                throw invalidCursor();
            }

            return new EmotionListCursor(
                    EmotionSearchBounds.of(
                            Double.parseDouble(fields[1]), Double.parseDouble(fields[2]),
                            Double.parseDouble(fields[3]), Double.parseDouble(fields[4])
                    ),
                    Instant.parse(fields[5]),
                    Instant.parse(fields[6]),
                    Long.parseLong(fields[7]),
                    groupCursor ? UUID.fromString(fields[8]) : null
            );
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalidCursor();
        }
    }

    public static EmotionListCursor decodeWithoutBounds(String encoded) {
        try {
            String[] fields = decodeFields(encoded);
            if (fields.length != WITHOUT_BOUNDS_FIELD_COUNT || !WITHOUT_BOUNDS_VERSION.equals(fields[0])) {
                throw invalidCursor();
            }
            return EmotionListCursor.ofWithoutBounds(Instant.parse(fields[1]), Instant.parse(fields[2]),
                    Long.parseLong(fields[3]), fields[4].isEmpty() ? null : UUID.fromString(fields[4]));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalidCursor();
        }
    }

    public static String encode(EmotionListCursor cursor) {
        EmotionSearchBounds bounds = cursor.bounds();
        if (bounds == null) {
            return encodePayload(String.join(FIELD_DELIMITER, WITHOUT_BOUNDS_VERSION, cursor.snapshotAt().toString(),
                    cursor.lastItemCreatedAt().toString(), Long.toString(cursor.lastId()),
                    cursor.groupId() == null ? "" : cursor.groupId().toString()));
        }
        String payload = String.join(
                FIELD_DELIMITER,
                cursor.groupId() == null ? WITHIN_BOUNDS_VERSION : "2",
                Double.toString(bounds.minLongitude()),
                Double.toString(bounds.minLatitude()),
                Double.toString(bounds.maxLongitude()),
                Double.toString(bounds.maxLatitude()),
                cursor.snapshotAt().toString(),
                cursor.lastItemCreatedAt().toString(),
                Long.toString(cursor.lastId())
        );
        if (cursor.groupId() != null) {
            payload += FIELD_DELIMITER + cursor.groupId();
        }
        return encodePayload(payload);
    }

    private static String[] decodeFields(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_CURSOR_LENGTH) {
            throw invalidCursor();
        }
        String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        return payload.split(Pattern.quote(FIELD_DELIMITER), -1);
    }

    private static String encodePayload(String payload) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static EmotionException invalidCursor() {
        return new EmotionException(EMOTION_INVALID_CURSOR);
    }
}
