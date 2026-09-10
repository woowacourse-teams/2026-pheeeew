package com.pheeeew.device.application.token;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenVerifier {

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.[A-Za-z0-9_-]{43}$"
    );

    public Optional<UUID> extractSessionId(String refreshToken) {
        if (refreshToken == null) {
            return Optional.empty();
        }

        Matcher matcher = TOKEN_PATTERN.matcher(refreshToken);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        return Optional.of(UUID.fromString(matcher.group(1)));
    }

    public boolean matches(String refreshToken, String storedHash) {
        if (extractSessionId(refreshToken).isEmpty()) {
            return false;
        }

        return RefreshTokenHasher.matches(refreshToken, storedHash);
    }
}
