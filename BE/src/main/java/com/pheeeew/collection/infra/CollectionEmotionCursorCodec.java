package com.pheeeew.collection.infra;

import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_INVALID_CURSOR;

import com.pheeeew.auth.infra.jwt.JwtProperties;
import com.pheeeew.collection.application.dto.CollectionDailyEmotionCursor;
import com.pheeeew.collection.exception.CollectionException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CollectionEmotionCursorCodec {

    private static final String DAILY_VERSION = "collection-daily-emotions-1";
    private static final String ALGORITHM = "SHA256withRSA";
    private static final int MAX_CURSOR_LENGTH = 2048;

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public CollectionEmotionCursorCodec(JwtProperties properties) {
        this.privateKey = properties.privateKey();
        this.publicKey = properties.publicKey();
    }

    public String encodeDaily(CollectionDailyEmotionCursor cursor) {
        return sign(String.join("|", DAILY_VERSION, cursor.deviceId().toString(), cursor.date().toString(),
                cursor.snapshotAt().toString(), cursor.lastCreatedAt().toString(), Long.toString(cursor.lastId())));
    }

    public CollectionDailyEmotionCursor decodeDaily(String encoded) {
        String[] fields = verifiedFields(encoded);
        try {
            return CollectionDailyEmotionCursor.of(UUID.fromString(fields[1]), LocalDate.parse(fields[2]),
                    Instant.parse(fields[3]), Instant.parse(fields[4]), Long.parseLong(fields[5]));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new CollectionException(COLLECTION_INVALID_CURSOR);
        }
    }

    private String sign(String value) {
        byte[] payload = value.getBytes(StandardCharsets.UTF_8);
        try {
            Signature signer = Signature.getInstance(ALGORITHM);
            signer.initSign(privateKey);
            signer.update(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payload) + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("컬렉션 감정 커서에 서명할 수 없습니다.", exception);
        }
    }

    private String[] verifiedFields(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_CURSOR_LENGTH) {
            throw new CollectionException(COLLECTION_INVALID_CURSOR);
        }
        try {
            String[] parts = encoded.split("\\.", -1);
            if (parts.length != 2) {
                throw new CollectionException(COLLECTION_INVALID_CURSOR);
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(payload);
            if (!verifier.verify(Base64.getUrlDecoder().decode(parts[1]))) {
                throw new CollectionException(COLLECTION_INVALID_CURSOR);
            }
            String[] fields = new String(payload, StandardCharsets.UTF_8).split("\\|", -1);
            if (fields.length != 6 || !DAILY_VERSION.equals(fields[0])) {
                throw new CollectionException(COLLECTION_INVALID_CURSOR);
            }
            return fields;
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new CollectionException(COLLECTION_INVALID_CURSOR);
        }
    }
}
