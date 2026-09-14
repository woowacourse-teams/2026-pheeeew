package com.pheeeew.device.infra.attestation;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.json.JsonParserFactory;

public record GoogleServiceAccountKey(String clientEmail, String tokenUri, RSAPrivateKey privateKey) {

    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String CLIENT_EMAIL_FIELD = "client_email";
    private static final String TOKEN_URI_FIELD = "token_uri";
    private static final String PRIVATE_KEY_FIELD = "private_key";
    private static final String PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_FOOTER = "-----END PRIVATE KEY-----";
    private static final String HTTPS_SCHEME = "https";
    private static final String HTTP_SCHEME = "http";
    private static final String LOOPBACK_IPV6_HOST = "[::1]";
    private static final Pattern LOOPBACK_IPV4_PATTERN =
            Pattern.compile("^127(?:\\.(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}$");

    public static GoogleServiceAccountKey from(String serviceAccountBase64) {
        Map<String, Object> serviceAccount = parseServiceAccount(serviceAccountBase64);

        return new GoogleServiceAccountKey(
                requireField(serviceAccount, CLIENT_EMAIL_FIELD),
                tokenUri(serviceAccount),
                parsePrivateKey(requireField(serviceAccount, PRIVATE_KEY_FIELD))
        );
    }

    private static Map<String, Object> parseServiceAccount(String serviceAccountBase64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(serviceAccountBase64.trim());
            String serviceAccountJson = new String(decoded, StandardCharsets.UTF_8);
            return JsonParserFactory.getJsonParser().parseMap(serviceAccountJson);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Play Integrity 서비스 계정 설정이 올바르지 않습니다.");
        }
    }

    private static String requireField(Map<String, Object> serviceAccount, String field) {
        Object value = serviceAccount.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Play Integrity 서비스 계정 설정에 " + field + " 이 없습니다.");
        }
        return text;
    }

    private static String tokenUri(Map<String, Object> serviceAccount) {
        Object value = serviceAccount.get(TOKEN_URI_FIELD);
        if (value instanceof String text && !text.isBlank()) {
            return requireTokenExchangeUri(text);
        }
        return DEFAULT_TOKEN_URI;
    }

    private static String requireTokenExchangeUri(String tokenUri) {
        URI parsed = parseTokenUri(tokenUri);
        if (parsed.isOpaque() || parsed.getScheme() == null || parsed.getHost() == null
                || parsed.getUserInfo() != null || !isAllowedTransport(parsed)) {
            throw new IllegalStateException("Play Integrity 서비스 계정 token_uri 설정이 올바르지 않습니다.");
        }
        return tokenUri;
    }

    private static URI parseTokenUri(String tokenUri) {
        try {
            return new URI(tokenUri);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Play Integrity 서비스 계정 token_uri 설정이 올바르지 않습니다.");
        }
    }

    private static boolean isAllowedTransport(URI tokenUri) {
        String scheme = tokenUri.getScheme().toLowerCase(Locale.ROOT);
        if (HTTPS_SCHEME.equals(scheme)) {
            return true;
        }
        return HTTP_SCHEME.equals(scheme) && isLoopbackLiteral(tokenUri.getHost());
    }

    private static boolean isLoopbackLiteral(String host) {
        return LOOPBACK_IPV6_HOST.equals(host) || LOOPBACK_IPV4_PATTERN.matcher(host).matches();
    }

    private static RSAPrivateKey parsePrivateKey(String privateKeyPem) {
        String base64Key = privateKeyPem
                .replace(PEM_HEADER, "")
                .replace(PEM_FOOTER, "")
                .replaceAll("\\s", "");
        try {
            byte[] encodedKey = Base64.getDecoder().decode(base64Key);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(encodedKey));
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException exception) {
            throw new IllegalStateException("Play Integrity 서비스 계정 개인 키 설정이 올바르지 않습니다.");
        }
    }

    @Override
    public String toString() {
        return "GoogleServiceAccountKey[clientEmail=<redacted>, tokenUri=" + tokenUri + ", privateKey=<redacted>]";
    }
}
