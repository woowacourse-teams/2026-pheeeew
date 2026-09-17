package com.pheeeew.sigh.infra;

import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SighLocationConfig {

    @Bean
    public SecureRandom sighLocationRandom() {
        return new SecureRandom();
    }
}
