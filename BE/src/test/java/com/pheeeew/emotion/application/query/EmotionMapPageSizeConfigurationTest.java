package com.pheeeew.emotion.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.pheeeew.common.exception.CommonErrorCode;
import com.pheeeew.common.exception.PheeeewException;
import com.pheeeew.emotion.application.mapcache.PinCandidateCache;
import com.pheeeew.emotion.application.mapcache.RegionSummaryCache;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EmotionMapPageSizeConfigurationTest {

    @ParameterizedTest
    @CsvSource({"dev,1000", "prod,200", "local,200", "test,200"})
    void 실제_프로필_설정으로_기본값과_허용_상한을_적용한다(String profile, int maximum) {
        // given
        var runner = contextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(EmotionMapQueryService.class)
                .withPropertyValues("spring.profiles.active=" + profile);

        // when / then
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var service = context.getBean(EmotionMapQueryService.class);
            assertThat(service.resolveMapPageSize(null)).isEqualTo(200);
            assertThat(service.resolveMapPageSize(1)).isEqualTo(1);
            assertThat(service.resolveMapPageSize(maximum)).isEqualTo(maximum);
            assertThatThrownBy(() -> service.resolveMapPageSize(maximum + 1))
                    .isInstanceOfSatisfying(PheeeewException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 400, 500, 1000})
    void 개발_실험_범위의_요청_개수를_줄이지_않는다(int requestedSize) {
        // given
        var service = service(1000);

        // when / then
        assertThat(service.resolveMapPageSize(requestedSize)).isEqualTo(requestedSize);
    }

    @ParameterizedTest
    @CsvSource({"200,0", "200,-1", "200,201", "200,1000",
            "1000,0", "1000,-1", "1000,1001", "1000,2147483647"})
    void 범위를_벗어난_요청은_잘라내지_않고_공통_400_오류로_거부한다(int maximum, int requestedSize) {
        // given
        var service = service(maximum);

        // when / then
        assertThatThrownBy(() -> service.resolveMapPageSize(requestedSize))
                .isInstanceOfSatisfying(PheeeewException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 199, 1001})
    void 기본_개수를_허용하지_않거나_실험_범위를_넘는_설정은_기동을_거부한다(int maximum) {
        // given
        var runner = contextRunner().withUserConfiguration(EmotionMapQueryService.class)
                .withPropertyValues("pheeeew.emotion-map.max-page-size=" + maximum);

        // when / then
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void 상한_설정이_없어도_200개를_기본으로_제한한다() {
        // given
        var runner = contextRunner().withUserConfiguration(EmotionMapQueryService.class);

        // when / then
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var service = context.getBean(EmotionMapQueryService.class);
            assertThat(service.resolveMapPageSize(null)).isEqualTo(200);
            assertThatThrownBy(() -> service.resolveMapPageSize(201))
                    .isInstanceOfSatisfying(PheeeewException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
        });
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(EmotionMapDbQueryService.class, () -> mock(EmotionMapDbQueryService.class))
                .withBean(RegionSummaryCache.class, () -> mock(RegionSummaryCache.class))
                .withBean(PinCandidateCache.class, () -> mock(PinCandidateCache.class));
    }

    private EmotionMapQueryService service(int maximum) {
        return new EmotionMapQueryService(Clock.systemUTC(), mock(EmotionMapDbQueryService.class),
                mock(RegionSummaryCache.class), mock(PinCandidateCache.class), maximum);
    }
}
