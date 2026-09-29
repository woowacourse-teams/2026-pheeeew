package com.pheeeew.groups.application;

import java.security.SecureRandom;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class InviteCodeGenerator {

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int LENGTH = 6;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static String normalize(String inviteCode) {
        if (inviteCode == null) {
            return null;
        }

        return inviteCode.strip()
                .toUpperCase(Locale.ROOT)
                .replace('I', '1')
                .replace('L', '1')
                .replace('O', '0');
    }

    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int index = 0; index < LENGTH; index++) {
            code.append(ALPHABET.charAt(SECURE_RANDOM.nextInt(ALPHABET.length())));
        }

        return code.toString();
    }
}
