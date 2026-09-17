package com.pheeeew.device.infra.attestation.appattest;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableConfigurationProperties(AppAttestProperties.class)
@Configuration
public class AppAttestConfig {

    @Bean
    public AppAttestObjectDecoder appAttestObjectDecoder() {
        return new AppAttestObjectDecoder();
    }

    @Bean
    public AppAttestCertificateChainValidator appAttestCertificateChainValidator() {
        return new AppAttestCertificateChainValidator();
    }
}
