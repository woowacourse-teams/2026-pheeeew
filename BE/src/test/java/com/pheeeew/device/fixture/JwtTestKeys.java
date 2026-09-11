package com.pheeeew.device.fixture;

import com.pheeeew.device.infra.jwt.JwtProperties;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class JwtTestKeys {

    private static final int RSA_KEY_SIZE = 2048;
    private static final JwtProperties 기본_키 = 새_키_쌍();
    private static final JwtProperties 다른_키 = 새_키_쌍();

    private JwtTestKeys() {
    }

    public static JwtProperties 기본_키_설정() {
        return 기본_키;
    }

    public static JwtProperties 다른_키_설정() {
        return 다른_키;
    }

    public static JwtProperties 새_키_쌍() {
        KeyPair keyPair = 생성한다();
        return new JwtProperties(
                Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
        );
    }

    private static KeyPair 생성한다() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_KEY_SIZE);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
