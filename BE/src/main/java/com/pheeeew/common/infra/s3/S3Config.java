package com.pheeeew.common.infra.s3;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@EnableConfigurationProperties(S3Properties.class)
@Configuration(proxyBeanMethods = false)
public class S3Config {

    @Bean
    public DefaultCredentialsProvider s3CredentialsProvider() {
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean
    public S3Client s3Client(S3Properties properties, DefaultCredentialsProvider s3CredentialsProvider) {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(s3CredentialsProvider)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(S3Properties properties, DefaultCredentialsProvider s3CredentialsProvider) {
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(s3CredentialsProvider)
                .build();
    }
}
