package com.pheeeew.groups.application;

import static com.pheeeew.groups.exception.GroupErrorCode.GROUP_INVALID_CURSOR;

import com.pheeeew.groups.application.dto.GroupCursor;
import com.pheeeew.groups.exception.GroupException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;

public final class GroupCursorCodec {

    private static final String EXPLORE_VERSION = "1";
    private static final String FIELD_DELIMITER = "|";
    private static final int FIELD_COUNT = 3;
    private static final int MAX_CURSOR_LENGTH = 128;
    private static final GroupCursor EXPLORE_INITIAL =
            GroupCursor.of(Instant.parse("9999-12-31T23:59:59Z"), Long.MAX_VALUE);

    private GroupCursorCodec() {
    }

    public static GroupCursor decodeExploreOrInitial(String encoded) {
        return decodeOrInitial(encoded, EXPLORE_VERSION, EXPLORE_INITIAL);
    }

    public static String encodeExplore(Instant lastCreatedAt, long lastId) {
        return encode(EXPLORE_VERSION, lastCreatedAt, lastId);
    }

    private static GroupCursor decodeOrInitial(String encoded, String version, GroupCursor initial) {
        if (encoded == null || encoded.isBlank()) {
            return initial;
        }
        if (encoded.length() > MAX_CURSOR_LENGTH) {
            throw invalidCursor();
        }

        try {
            String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] fields = payload.split(Pattern.quote(FIELD_DELIMITER), -1);
            if (fields.length != FIELD_COUNT || !version.equals(fields[0])) {
                throw invalidCursor();
            }

            return GroupCursor.of(Instant.parse(fields[1]), requirePositive(Long.parseLong(fields[2])));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalidCursor();
        }
    }

    private static String encode(String version, Instant lastCreatedAt, long lastId) {
        String payload =
                String.join(FIELD_DELIMITER, version, lastCreatedAt.toString(), Long.toString(lastId));

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static long requirePositive(long lastId) {
        if (lastId < 1) {
            throw invalidCursor();
        }

        return lastId;
    }

    private static GroupException invalidCursor() {
        return new GroupException(GROUP_INVALID_CURSOR);
    }
}
