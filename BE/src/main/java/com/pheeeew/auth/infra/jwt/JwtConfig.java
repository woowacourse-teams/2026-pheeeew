package com.pheeeew.auth.infra.jwt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@EnableConfigurationProperties({JwtProperties.class, TokenProperties.class})
@Configuration
public class JwtConfig {

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties jwtProperties) {
        return NimbusJwtEncoder.withKeyPair(jwtProperties.publicKey(), jwtProperties.privateKey())
                .algorithm(SignatureAlgorithm.RS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties jwtProperties, AccessTokenJwtValidator accessTokenJwtValidator) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withPublicKey(jwtProperties.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(AccessTokenContract.ISSUER),
                accessTokenJwtValidator
        ));
        return jwtDecoder;
    }
}
