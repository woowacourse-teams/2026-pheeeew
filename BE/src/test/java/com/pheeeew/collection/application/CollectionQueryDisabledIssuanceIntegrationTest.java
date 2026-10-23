package com.pheeeew.collection.application;

import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_빌더;
import static com.pheeeew.collection.fixture.DeviceDailyStampFixture.일별_스탬프를_저장한다;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pheeeew.collection.domain.repository.CollectionAssetRepository;
import com.pheeeew.collection.domain.repository.CollectionCardRepository;
import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
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
@TestPropertySource(properties = {"pheeeew.s3.bucket=collection-test-assets", "pheeeew.s3.region=ap-northeast-2",
        "pheeeew.s3.key-prefix=pheeeew/development/", "pheeeew.collection.card-version="})
class CollectionQueryDisabledIssuanceIntegrationTest {

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
    void 발급_버전이_비어도_미수령_진행도는_조회할_수_있다() {
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
    void 발급_버전이_비어도_기존_카드의_이미지는_공통_S3_설정으로_조회한다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        cards.saveAndFlush(기본_카드_빌더().deviceId(device.getId()).build());
        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 100);

        // when
        var result = service.findToday(device.getPublicId());

        // then
        assertThat(result.items()).filteredOn(item -> item.state() == EmotionState.ANGRY)
                .singleElement().satisfies(item -> {
                    assertThat(item.card()).isNotNull();
                    assertThat(item.card().images().characterUrl()).startsWith(
                            "https://collection-test-assets.s3.ap-northeast-2.amazonaws.com/pheeeew/development/collection-cards/v1/");
                });
        assertThat(cards.count()).isEqualTo(1);
    }
    @Test
    void 신규_수령이_비활성화돼도_과거_카드_상세는_조회한다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        var card = cards.saveAndFlush(기본_카드_빌더().deviceId(device.getId())
                .cardDate(TODAY.minusDays(1)).build());

        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY.minusDays(1), EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY.minusDays(1), EmotionState.ANGRY, 100);

        // when
        var result = service.findDetail(device.getPublicId(), card.getPublicId());

        // then
        assertThat(result.cardId()).isEqualTo(card.getPublicId());
        assertThat(result.images().characterUrl()).endsWith("/v1/characters/angry/01.png");
    }
}
