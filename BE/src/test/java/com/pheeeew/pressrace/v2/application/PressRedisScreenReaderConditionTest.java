package com.pheeeew.pressrace.v2.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pheeeew.pressrace.v2.infra.PressRedisStore;
import java.time.Clock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 측정 전후 비교는 같은 이미지에서 설정만 바꿔 기본 폴링의 출처를 고른다.
 * 설정 이름이나 기본값이 어긋나면 "전" 측정이 조용히 새 경로를 재게 되므로 조건 판정을 고정한다.
 */
class PressRedisScreenReaderConditionTest {

    @ParameterizedTest
    @CsvSource(value = {
            "REDIS, NULL, true",
            "REDIS, RANK, true",
            "REDIS, BOARD, false",
            "DATABASE, NULL, false",
            "MEMORY, NULL, false",
            "NULL, NULL, false"
    }, nullValues = "NULL")
    void 기본_폴링은_REDIS_모드에서_출처_설정이_없거나_RANK일_때만_순위표_직접_읽기를_쓴다(
            String writeMode, String snapshotSource, boolean readsRank) {
        // given
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withBean(PressRedisStore.class, () -> mock(PressRedisStore.class))
                .withBean(PressRedisSeeder.class, () -> mock(PressRedisSeeder.class))
                .withBean(PressRedisTodayBoard.class, () -> mock(PressRedisTodayBoard.class))
                .withBean(Clock.class, Clock::systemUTC)
                .withUserConfiguration(PressRedisScreenReader.class);
        if (writeMode != null) {
            contextRunner = contextRunner.withPropertyValues("pheeeew.press-race.write-mode=" + writeMode);
        }
        if (snapshotSource != null) {
            contextRunner = contextRunner.withPropertyValues("pheeeew.press-race.snapshot-source=" + snapshotSource);
        }

        // when, then
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeanProvider(PressRedisScreenReader.class).getIfAvailable() != null)
                    .isEqualTo(readsRank);
        });
    }
}
