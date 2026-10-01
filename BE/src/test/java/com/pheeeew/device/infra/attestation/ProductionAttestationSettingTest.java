package com.pheeeew.device.infra.attestation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

class ProductionAttestationSettingTest {

    @Test
    void 운영_프로필은_안드로이드와_iOS_무결성_증명을_모두_강제한다() throws IOException {
        // given
        Map<String, Object> pheeeew = productionSetting();

        // when
        Object playIntegrity = requireAttestationOf(pheeeew, "play-integrity");
        Object appAttest = requireAttestationOf(pheeeew, "app-attest");

        // then
        assertThat(playIntegrity).isEqualTo(true);
        assertThat(appAttest).isEqualTo(true);
    }

    @Test
    void 운영_프로필은_무결성_검증_건너뛰기를_켜지_않는다() throws IOException {
        // given
        Map<String, Object> pheeeew = productionSetting();

        // when
        Object skipVerification = nested(pheeeew, "play-integrity").get("skip-verification");

        // then
        assertThat(skipVerification).isNull();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> productionSetting() throws IOException {
        try (InputStream source = new ClassPathResource("application-prod.yml").getInputStream()) {
            Map<String, Object> root = new Yaml().load(source);
            return (Map<String, Object>) root.get("pheeeew");
        }
    }

    private Object requireAttestationOf(Map<String, Object> pheeeew, String platform) {
        return nested(pheeeew, platform).get("require-attestation");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nested(Map<String, Object> pheeeew, String key) {
        return (Map<String, Object>) pheeeew.get(key);
    }
}
