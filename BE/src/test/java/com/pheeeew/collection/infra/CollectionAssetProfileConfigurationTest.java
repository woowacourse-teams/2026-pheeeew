package com.pheeeew.collection.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class CollectionAssetProfileConfigurationTest {

    @Test
    void 개발은_환경_변수가_없으면_설정을_해석할_수_없다() throws IOException {
        // given
        var environment = load("dev");

        // when / then
        assertThatThrownBy(() -> environment.getProperty("pheeeew.collection.card-version"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 운영은_환경_변수가_없으면_버전을_비워_발급을_활성화하지_않는다() throws IOException {
        // given
        var environment = load("prod");

        // when / then
        assertThat(environment.getProperty("pheeeew.collection.card-version")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void 환경_변수로_발급_버전을_덮어쓸_수_있다(String profile) throws IOException {
        // given
        var environment = load(profile);
        environment.getPropertySources().addFirst(new MapPropertySource("collection-environment", Map.of(
                "COLLECTION_CARD_VERSION", "v2")));

        // when / then
        assertThat(environment.getProperty("pheeeew.collection.card-version")).isEqualTo("v2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void 명시적으로_빈_환경_변수는_다른_값으로_대체하지_않는다(String profile) throws IOException {
        // given
        var environment = load(profile);
        environment.getPropertySources().addFirst(new MapPropertySource("collection-environment", Map.of(
                "COLLECTION_CARD_VERSION", "")));

        // when / then
        assertThat(environment.getProperty("pheeeew.collection.card-version")).isEmpty();
    }

    private StandardEnvironment load(String profile) throws IOException {
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        var sources = new YamlPropertySourceLoader().load(profile,
                new ClassPathResource("application-" + profile + ".yml"));
        sources.forEach(source -> environment.getPropertySources().addLast(source));
        return environment;
    }
}
