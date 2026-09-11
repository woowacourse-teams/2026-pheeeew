package com.pheeeew.sigh.application;

import static com.pheeeew.sigh.exception.SighErrorCode.SIGH_INVALID_CURSOR;

import com.pheeeew.sigh.application.dto.SighListCursor;
import com.pheeeew.sigh.application.dto.SighSearchBounds;
import com.pheeeew.sigh.exception.SighException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;

public final class SighListCursorCodec {

    private static final String VERSION = "1";
    private static final String FIELD_DELIMITER = "|";
    private static final int FIELD_COUNT = 8;
    private static final int MAX_CURSOR_LENGTH = 2_048;

    private SighListCursorCodec() {
    }

    public static SighListCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_CURSOR_LENGTH) {
            throw invalidCursor();
        }

        try {
            String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] fields = payload.split(Pattern.quote(FIELD_DELIMITER), -1);
            if (fields.length != FIELD_COUNT || !VERSION.equals(fields[0])) {
                throw invalidCursor();
            }

            return SighListCursor.of(
                    SighSearchBounds.of(
                            Double.parseDouble(fields[1]), Double.parseDouble(fields[2]),
                            Double.parseDouble(fields[3]), Double.parseDouble(fields[4])
                    ),
                    Instant.parse(fields[5]),
                    Instant.parse(fields[6]),
                    Long.parseLong(fields[7])
            );
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalidCursor();
        }
    }

    public static String encode(SighListCursor cursor) {
        SighSearchBounds bounds = cursor.bounds();
        String payload = String.join(
                FIELD_DELIMITER,
                VERSION,
                Double.toString(bounds.minLongitude()),
                Double.toString(bounds.minLatitude()),
                Double.toString(bounds.maxLongitude()),
                Double.toString(bounds.maxLatitude()),
                cursor.snapshotAt().toString(),
                cursor.lastItemCreatedAt().toString(),
                Long.toString(cursor.lastId())
        );
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static SighException invalidCursor() {
        return new SighException(SIGH_INVALID_CURSOR);
    }
}
