package com.pheeeew.collection.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.collection.domain.repository.CollectionAssetRepository;
import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.collection.infra.CollectionEmotionCursorCodec;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@PostgisDataJpaTest
@Import({CollectionDailyQueryService.class, CollectionEmotionQueryService.class, CollectionEmotionCursorCodec.class,
        CollectionProgressRepository.class, CollectionAssetRepository.class,
        CollectionDailyMissingAssetsIntegrationTest.AssetsConfiguration.class})
class CollectionDailyMissingAssetsIntegrationTest {

    @Autowired private CollectionDailyQueryService service;
    @Autowired private DeviceRepository devices;
    @Autowired private JdbcClient jdbc;

    @Test
    void 카드가_없는_날은_이미지_설정이_없어도_프레스를_조회한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        LocalDate date = LocalDate.of(2024, 2, 29);
        개인_프레스를_저장한다(jdbc, owner.getId(), date, EmotionState.ANGRY, 7);

        // when
        var result = service.findDaily(owner.getPublicId(), date, null);

        // then
        assertThat(result.card()).isNull();
        assertThat(result.pressCount()).isEqualTo(7);
        assertThat(result.emotions().items()).isEmpty();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AssetsConfiguration {

        @Bean
        CollectionAssetService collectionAssetService(CollectionAssetRepository repository) {
            return new CollectionAssetService(repository, "", "");
        }
    }
}
