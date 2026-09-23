package com.pheeeew.sigh.infra;

import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmotionLocationConfig {

    @Bean
    public SecureRandom emotionLocationRandom() {
        return new SecureRandom();
    }
}
