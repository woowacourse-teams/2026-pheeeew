package com.pheeeew.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
public class SharedJwtTestConfiguration {

    private static final int RSA_KEY_SIZE = 2048;

    @Bean
    DynamicPropertyRegistrar jwtKeyProperties() {
        KeyPair keyPair = generateRsaKeyPair();
        String privateKeyBase64 = encode(keyPair.getPrivate().getEncoded());
        String publicKeyBase64 = encode(keyPair.getPublic().getEncoded());

        return registry -> {
            registry.add("pheeeew.jwt.private-key-base64", () -> privateKeyBase64);
            registry.add("pheeeew.jwt.public-key-base64", () -> publicKeyBase64);
        };
    }

    private KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_KEY_SIZE);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("테스트용 RSA 키 쌍을 만들 수 없습니다.", exception);
        }
    }

    private String encode(byte[] encodedKey) {
        return Base64.getEncoder().encodeToString(encodedKey);
    }
}
