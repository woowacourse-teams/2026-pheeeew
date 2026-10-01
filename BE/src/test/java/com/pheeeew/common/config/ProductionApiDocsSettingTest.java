package com.pheeeew.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

class ProductionApiDocsSettingTest {

    @Test
    void 운영_프로필은_API_문서를_노출하지_않는다() throws IOException {
        // given
        Map<String, Object> springdoc = productionSpringdoc();

        // when
        Object apiDocs = nested(springdoc, "api-docs").get("enabled");
        Object swaggerUi = nested(springdoc, "swagger-ui").get("enabled");

        // then
        assertThat(apiDocs).isEqualTo(false);
        assertThat(swaggerUi).isEqualTo(false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> productionSpringdoc() throws IOException {
        try (InputStream source = new ClassPathResource("application-prod.yml").getInputStream()) {
            Map<String, Object> root = new Yaml().load(source);
            return (Map<String, Object>) root.get("springdoc");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nested(Map<String, Object> springdoc, String key) {
        return (Map<String, Object>) springdoc.get(key);
    }
}
