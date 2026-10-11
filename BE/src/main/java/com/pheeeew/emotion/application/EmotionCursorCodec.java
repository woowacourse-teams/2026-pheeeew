package com.pheeeew.emotion.application;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_INVALID_CURSOR;

import com.pheeeew.emotion.application.dto.EmotionCursor;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.emotion.exception.EmotionException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

public final class EmotionCursorCodec {

    private static final String WITHIN_BOUNDS_VERSION = "1";
    private static final String WITHOUT_BOUNDS_VERSION = "3";
    private static final String FILTERED_WITHIN_BOUNDS_VERSION = "4";
    private static final String FILTERED_WITHOUT_BOUNDS_VERSION = "5";
    private static final String FIELD_DELIMITER = "|";
    private static final int WITHIN_BOUNDS_FIELD_COUNT = 8;
    private static final int WITHOUT_BOUNDS_FIELD_COUNT = 5;
    private static final int FILTERED_WITHIN_BOUNDS_FIELD_COUNT = 13;
    private static final int FILTERED_WITHOUT_BOUNDS_FIELD_COUNT = 9;
    private static final int MAX_CURSOR_LENGTH = 2_048;

    private EmotionCursorCodec() {
    }

    public static EmotionCursor decodeWithinBounds(String encoded) {
        try {
            String[] fields = decodeFields(encoded);
            boolean groupCursor = fields.length == WITHIN_BOUNDS_FIELD_COUNT + 1 && "2".equals(fields[0]);
            if (!groupCursor && (fields.length != WITHIN_BOUNDS_FIELD_COUNT || !WITHIN_BOUNDS_VERSION.equals(fields[0]))) {
                throw invalidCursor();
            }

            return EmotionCursor.ofWithinBounds(
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

    public static String encode(EmotionCursor cursor) {
        if (!cursor.filter().equals(EmotionSearchFilter.of(null, null, null, null, cursor.groupId()))) {
            return encodeFiltered(cursor);
        }
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

    public static EmotionCursor decodeFilteredWithoutBounds(String encoded) {
        try {
            String[] fields = decodeFields(encoded);
            if (!FILTERED_WITHOUT_BOUNDS_VERSION.equals(fields[0])) {
                return decodeWithoutBounds(encoded);
            }

            if (fields.length != FILTERED_WITHOUT_BOUNDS_FIELD_COUNT) {
                throw invalidCursor();
            }

            return decodeFiltered(fields, null);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalidCursor();
        }
    }

    public static EmotionCursor decodeFilteredWithinBounds(String encoded) {
        try {
            String[] fields = decodeFields(encoded);
            if (!FILTERED_WITHIN_BOUNDS_VERSION.equals(fields[0])) {
                return decodeWithinBounds(encoded);
            }
            if (fields.length != FILTERED_WITHIN_BOUNDS_FIELD_COUNT) {
                throw invalidCursor();
            }

            EmotionSearchBounds bounds = EmotionSearchBounds.of(Double.parseDouble(fields[9]),
                    Double.parseDouble(fields[10]), Double.parseDouble(fields[11]), Double.parseDouble(fields[12]));
            return decodeFiltered(fields, bounds);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalidCursor();
        }
    }

    private static String encodeFiltered(EmotionCursor cursor) {
        EmotionSearchFilter filter = cursor.filter();
        EmotionSearchBounds bounds = cursor.bounds();

        String payload = String.join(FIELD_DELIMITER,
                bounds == null ? FILTERED_WITHOUT_BOUNDS_VERSION : FILTERED_WITHIN_BOUNDS_VERSION,
                cursor.snapshotAt().toString(), cursor.lastItemCreatedAt().toString(), Long.toString(cursor.lastId()),
                cursor.groupId() == null ? "" : cursor.groupId().toString(),
                filter.startDate() == null ? "" : filter.startDate().toString(),
                filter.endDate() == null ? "" : filter.endDate().toString(), filter.contentType().name(), filter.scope().name());
        if (bounds != null) {
            payload += FIELD_DELIMITER + String.join(FIELD_DELIMITER,
                    Double.toString(bounds.minLongitude()), Double.toString(bounds.minLatitude()),
                    Double.toString(bounds.maxLongitude()), Double.toString(bounds.maxLatitude()));
        }

        return encodePayload(payload);
    }

    private static EmotionCursor decodeFiltered(String[] fields, EmotionSearchBounds bounds) {
        UUID groupId = fields[4].isEmpty() ? null : UUID.fromString(fields[4]);
        EmotionSearchFilter filter = EmotionSearchFilter.of(
                fields[5].isEmpty() ? null : LocalDate.parse(fields[5]),
                fields[6].isEmpty() ? null : LocalDate.parse(fields[6]),
                EmotionContentFilter.valueOf(fields[7]), EmotionSearchScope.valueOf(fields[8]), groupId);

        Instant snapshotAt = Instant.parse(fields[1]);
        Instant lastCreatedAt = Instant.parse(fields[2]);
        long lastId = Long.parseLong(fields[3]);
        EmotionCursor cursor = bounds == null
                ? EmotionCursor.ofWithoutBounds(snapshotAt, lastCreatedAt, lastId, groupId)
                : EmotionCursor.ofWithinBounds(bounds, snapshotAt, lastCreatedAt, lastId, groupId);
        return cursor.withFilter(filter);
    }

    private static EmotionCursor decodeWithoutBounds(String encoded) {
        try {
            String[] fields = decodeFields(encoded);
            if (fields.length != WITHOUT_BOUNDS_FIELD_COUNT || !WITHOUT_BOUNDS_VERSION.equals(fields[0])) {
                throw invalidCursor();
            }
            return EmotionCursor.ofWithoutBounds(Instant.parse(fields[1]), Instant.parse(fields[2]),
                    Long.parseLong(fields[3]), fields[4].isEmpty() ? null : UUID.fromString(fields[4]));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalidCursor();
        }
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
