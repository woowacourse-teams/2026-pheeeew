package com.pheeeew.device.infra.attestation;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@EnableConfigurationProperties(PlayIntegrityProperties.class)
@Configuration
public class PlayIntegrityConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClient googleApiRestClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .build()
        );
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public GoogleAccessTokenProvider googleAccessTokenProvider(
            RestClient googleApiRestClient,
            PlayIntegrityProperties playIntegrityProperties
    ) {
        return new GoogleAccessTokenProvider(googleApiRestClient, playIntegrityProperties);
    }

    @Bean
    public PlayIntegrityTokenDecoder playIntegrityTokenDecoder(
            RestClient googleApiRestClient,
            GoogleAccessTokenProvider googleAccessTokenProvider,
            PlayIntegrityProperties playIntegrityProperties
    ) {
        return new PlayIntegrityTokenDecoder(
                googleApiRestClient,
                googleAccessTokenProvider,
                playIntegrityProperties
        );
    }
}
