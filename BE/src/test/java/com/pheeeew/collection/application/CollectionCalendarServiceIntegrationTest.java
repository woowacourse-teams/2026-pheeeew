package com.pheeeew.collection.application;

import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_빌더;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.collection.fixture.DeviceDailyStampFixture.일별_스탬프를_저장한다;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.collection.domain.repository.CollectionAssetRepository;
import com.pheeeew.collection.domain.repository.CollectionCardRepository;
import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.collection.infra.CollectionAssetConfiguration;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.jdbc.core.simple.JdbcClient;

@PostgisDataJpaTest
@Import({CollectionQueryService.class, CollectionProgressRepository.class,
        CollectionAssetConfiguration.class, CollectionAssetRepository.class})
@TestPropertySource(properties = {"pheeeew.s3.bucket=collection-test-assets", "pheeeew.s3.region=ap-northeast-2",
        "pheeeew.s3.key-prefix=pheeeew/development/", "pheeeew.collection.card-version=v1"})
class CollectionCalendarServiceIntegrationTest {

    @Autowired private CollectionQueryService service;
    @Autowired private DeviceRepository devices;
    @Autowired private CollectionCardRepository cards;
    @Autowired private JdbcClient jdbc;

    @Test
    void 선택한_월의_본인_카드만_윤년_말일까지_날짜순으로_조회한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var other = devices.saveAndFlush(기본_기기_빌더().build());
        cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId())
                .cardDate(LocalDate.of(2024, 1, 31)).combinationIndex(0).build());
        var last = cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId())
                .cardDate(LocalDate.of(2024, 2, 29)).combinationIndex(1).build());
        var first = cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId())
                .cardDate(LocalDate.of(2024, 2, 1)).combinationIndex(2).build());
        cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId())
                .cardDate(LocalDate.of(2024, 3, 1)).combinationIndex(3).build());
        cards.saveAndFlush(기본_카드_빌더().deviceId(other.getId())
                .cardDate(LocalDate.of(2024, 2, 15)).build());

        // when
        var result = service.findCalendar(owner.getPublicId(), YearMonth.of(2024, 2));

        // then
        assertThat(result).extracting(item -> item.cardId())
                .containsExactly(first.getPublicId(), last.getPublicId());
        assertThat(result).containsExactly(service.findDetail(owner.getPublicId(), first.getPublicId()),
                service.findDetail(owner.getPublicId(), last.getPublicId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-12", "0001-01", "9999-12"})
    void 연말과_지원_연도_경계에서도_한_달의_카드를_전부_조회한다(String value) {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var month = YearMonth.parse(value);
        for (int day = 1; day <= 31; day++) {
            cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId())
                    .cardDate(month.atDay(day)).combinationIndex(day - 1).build());
        }

        // when
        var result = service.findCalendar(owner.getPublicId(), month);

        // then
        assertThat(result).hasSize(31);
        assertThat(result.getFirst().date()).isEqualTo(month.atDay(1));
        assertThat(result.getLast().date()).isEqualTo(month.atDay(31));
    }

    @Test
    void 오늘_발급한_카드도_포함하고_미발급_날짜의_활동으로_카드를_만들지_않는다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        일별_스탬프를_저장한다(jdbc, owner.getId(), today, EmotionState.ANGRY, 3);
        var empty = service.findCalendar(owner.getPublicId(), YearMonth.from(today));
        var card = cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId()).cardDate(today).build());

        // when
        var issued = service.findCalendar(owner.getPublicId(), YearMonth.from(today));

        // then
        assertThat(empty).isEmpty();
        assertThat(issued).extracting(item -> item.cardId()).containsExactly(card.getPublicId());
        assertThat(issued.getFirst().stampCount()).isEqualTo(3);
    }
}
