package com.pheeeew.auth.infra.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.auth.fixture.AccessTokenFixture;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;

class AccessTokenJwtValidatorTest {

    private static final UUID 기기_공개_식별자 = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");

    private final AccessTokenJwtValidator validator = new AccessTokenJwtValidator();

    @Test
    void 용도가_액세스이고_대상이_기기_공개_식별자면_통과한다() {
        // given
        Jwt jwt = AccessTokenFixture.액세스_토큰_클레임(기기_공개_식별자);

        // when
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void 용도가_액세스가_아니면_토큰_자체를_거부한다() {
        // given
        Jwt jwt = AccessTokenFixture.용도가_액세스가_아닌_클레임(기기_공개_식별자);

        // when
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // then
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .extracting("errorCode")
                .containsOnly(BearerTokenErrorCodes.INVALID_TOKEN);
    }

    @Test
    void 용도_클레임이_없으면_토큰_자체를_거부한다() {
        // given
        Jwt jwt = AccessTokenFixture.용도가_없는_클레임(기기_공개_식별자);

        // when
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // then
        assertThat(result.hasErrors()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"1", "not-a-uuid", "", "a8ce0347-6f21-4c62-9a7e"})
    void 대상이_기기_공개_식별자_형식이_아니면_디코딩_단계에서_거부한다(String subject) {
        // given
        Jwt jwt = AccessTokenFixture.대상이_기기_공개_식별자가_아닌_클레임(subject);

        // when
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // then
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .extracting("errorCode")
                .containsOnly(BearerTokenErrorCodes.INVALID_TOKEN);
    }
}
