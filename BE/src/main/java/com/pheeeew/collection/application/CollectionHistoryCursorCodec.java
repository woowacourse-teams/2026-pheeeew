package com.pheeeew.collection.application;

import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_INVALID_CURSOR;

import com.pheeeew.collection.application.dto.CollectionHistoryCursor;
import com.pheeeew.collection.exception.CollectionException;
import com.pheeeew.emotion.domain.EmotionState;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Base64;

public final class CollectionHistoryCursorCodec {

    private CollectionHistoryCursorCodec() {
    }

    public static String encode(CollectionHistoryCursor cursor) {
        String payload = String.join("|", "1", cursor.beforeDate().toString(),
                cursor.emotion() == null ? "" : cursor.emotion().name(),
                cursor.lastDate().toString(), Long.toString(cursor.lastId()));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static CollectionHistoryCursor decode(String encoded) {
        if (encoded.isBlank() || encoded.length() > 512) {
            throw new CollectionException(COLLECTION_INVALID_CURSOR);
        }
        try {
            String[] fields = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).split("\\|", -1);
            if (fields.length != 5 || !"1".equals(fields[0])) {
                throw new CollectionException(COLLECTION_INVALID_CURSOR);
            }
            return CollectionHistoryCursor.of(LocalDate.parse(fields[1]),
                    fields[2].isEmpty() ? null : EmotionState.valueOf(fields[2]), LocalDate.parse(fields[3]),
                    Long.parseLong(fields[4]));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new CollectionException(COLLECTION_INVALID_CURSOR);
        }
    }
}
