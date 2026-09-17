package com.pheeeew.report.application;

import static com.pheeeew.report.exception.BlockErrorCode.BLOCK_INVALID_CURSOR;

import com.pheeeew.report.exception.BlockException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

public final class BlockListCursorCodec {

    private static final String VERSION = "1";
    private static final String FIELD_DELIMITER = "|";
    private static final int FIELD_COUNT = 2;
    private static final int MAX_CURSOR_LENGTH = 128;
    private static final long INITIAL_LAST_ID = Long.MAX_VALUE;

    private BlockListCursorCodec() {
    }

    public static long decodeOrInitial(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return INITIAL_LAST_ID;
        }
        if (encoded.length() > MAX_CURSOR_LENGTH) {
            throw invalidCursor();
        }

        try {
            String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] fields = payload.split(Pattern.quote(FIELD_DELIMITER), -1);
            if (fields.length != FIELD_COUNT || !VERSION.equals(fields[0])) {
                throw invalidCursor();
            }

            return requirePositive(Long.parseLong(fields[1]));
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    public static String encode(long lastId) {
        String payload = String.join(FIELD_DELIMITER, VERSION, Long.toString(lastId));

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

    private static BlockException invalidCursor() {
        return new BlockException(BLOCK_INVALID_CURSOR);
    }
}
