package com.pheeeew.device.infra.attestation;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlayIntegrityMetricsTest {

    private static final String 판정_지표 = "pheeeew.device.attestation.verdict";
    private static final String 거절_지표 = "pheeeew.device.attestation.rejected";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PlayIntegrityMetrics metrics = new PlayIntegrityMetrics(registry);

    @Test
    void 판정은_앱_인식_결과와_기기_무결성_포함_여부로_분해된다() {
        // given / when
        metrics.recordVerdict("PLAY_RECOGNIZED", true);
        metrics.recordVerdict("PLAY_RECOGNIZED", true);
        metrics.recordVerdict("PLAY_RECOGNIZED", false);
        metrics.recordVerdict("UNRECOGNIZED_VERSION", false);

        // then
        assertThat(판정_수("PLAY_RECOGNIZED", "true")).isEqualTo(2);
        assertThat(판정_수("PLAY_RECOGNIZED", "false")).isOne();
        assertThat(판정_수("UNRECOGNIZED_VERSION", "false")).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLAY_RECOGNIZED", "PLAY_UNRECOGNIZED", "UNRECOGNIZED_VERSION", "UNEVALUATED"})
    void 문서에_있는_판정값은_그대로_태그가_된다(String appRecognitionVerdict) {
        // given / when
        metrics.recordVerdict(appRecognitionVerdict, true);

        // then
        assertThat(판정_수(appRecognitionVerdict, "true")).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOMETHING_NEW", "", "PLAY_RECOGNIZED ", "play_recognized"})
    void 알려지지_않은_판정값은_UNKNOWN_으로_묶어_태그가_늘어나지_않는다(String appRecognitionVerdict) {
        // given / when
        metrics.recordVerdict(appRecognitionVerdict, true);

        // then
        assertThat(판정_수("UNKNOWN", "true")).isOne();
        assertThat(지표_계열_수(판정_지표)).isOne();
    }

    @Test
    void 거절은_사유별로_분해되고_사유_종류만큼만_계열이_생긴다() {
        // given / when
        metrics.recordRejected(PlayIntegrityRejection.PACKAGE_MISMATCH);
        metrics.recordRejected(PlayIntegrityRejection.CHALLENGE_INVALID);
        metrics.recordRejected(PlayIntegrityRejection.CHALLENGE_INVALID);
        metrics.recordRejected(PlayIntegrityRejection.CREDENTIALS_MISSING);
        metrics.recordRejected(PlayIntegrityRejection.GOOGLE_UNAVAILABLE);

        // then
        assertThat(거절_수("PACKAGE_MISMATCH")).isOne();
        assertThat(거절_수("CHALLENGE_INVALID")).isEqualTo(2);
        assertThat(거절_수("CREDENTIALS_MISSING")).isOne();
        assertThat(거절_수("GOOGLE_UNAVAILABLE")).isOne();
        assertThat(지표_계열_수(거절_지표)).isEqualTo(4);
    }

    @Test
    void 어떤_지표도_기기_식별자나_challenge_를_담을_태그를_갖지_않는다() {
        // given
        metrics.recordVerdict("PLAY_RECOGNIZED", true);
        metrics.recordRejected(PlayIntegrityRejection.CHALLENGE_INVALID);
        metrics.recordAccepted();

        // when
        List<String> 태그_키들 = registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(Tag::getKey)
                .distinct()
                .toList();

        // then
        assertThat(태그_키들).containsExactlyInAnyOrder("appRecognition", "deviceIntegrity", "reason");
    }

    private double 판정_수(String appRecognition, String deviceIntegrity) {
        return registry.get(판정_지표)
                .tag("appRecognition", appRecognition)
                .tag("deviceIntegrity", deviceIntegrity)
                .counter()
                .count();
    }

    private double 거절_수(String reason) {
        return registry.get(거절_지표).tag("reason", reason).counter().count();
    }

    private long 지표_계열_수(String name) {
        return registry.getMeters().stream()
                .map(Meter::getId)
                .filter(id -> id.getName().equals(name))
                .count();
    }
}
