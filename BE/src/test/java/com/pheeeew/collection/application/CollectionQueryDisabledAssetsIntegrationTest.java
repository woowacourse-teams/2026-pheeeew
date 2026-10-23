package com.pheeeew.collection.application;

import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_ASSETS_NOT_READY;
import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_빌더;
import static com.pheeeew.collection.fixture.DeviceDailyStampFixture.일별_스탬프를_저장한다;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.pheeeew.collection.domain.repository.CollectionAssetRepository;
import com.pheeeew.collection.domain.repository.CollectionCardRepository;
import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.collection.exception.CollectionException;
import com.pheeeew.collection.infra.CollectionAssetConfiguration;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@PostgisDataJpaTest
@Import({CollectionQueryService.class, CollectionProgressRepository.class, CollectionAssetConfiguration.class,
        CollectionAssetRepository.class})
@TestPropertySource(properties = {"pheeeew.collection.asset-base-url=", "pheeeew.collection.card-version="})
class CollectionQueryDisabledAssetsIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    @Autowired
    private CollectionQueryService service;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private CollectionCardRepository cards;

    @Autowired
    private JdbcClient jdbc;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T03:00:00Z"));
    }

    @Test
    void 자산_설정이_비어도_미수령_진행도는_조회할_수_있다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 1);

        // when
        var result = service.findToday(device.getPublicId());

        // then
        assertThat(result.date()).isEqualTo(TODAY);
        assertThat(result.items()).hasSize(5).allSatisfy(item -> assertThat(item.card()).isNull());
        assertThat(result.items()).filteredOn(item -> item.state() == EmotionState.ANGRY)
                .singleElement().satisfies(item -> assertThat(item.stamps().count()).isEqualTo(1));
        assertThat(cards.count()).isZero();
    }

    @Test
    void 수령한_카드의_이미지_주소를_만들_수_없으면_미수령으로_숨기지_않고_오류를_반환한다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        cards.saveAndFlush(기본_카드_빌더().deviceId(device.getId()).build());

        // when / then
        assertThatThrownBy(() -> service.findToday(device.getPublicId()))
                .isInstanceOfSatisfying(CollectionException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(COLLECTION_ASSETS_NOT_READY));
        assertThat(cards.count()).isEqualTo(1);
    }
}
