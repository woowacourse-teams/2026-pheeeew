package com.pheeeew.collection;

import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_빌더;
import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_추첨_상태_빌더;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.collection.domain.CollectionCard;
import com.pheeeew.device.domain.Device;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@PostgisDataJpaTest
class CollectionStorageIntegrationTest {

    @Autowired
    private EntityManager entities;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void 같은_기기_날짜_감정은_다른_조합이어도_카드를_두_장_저장하지_못한다() {
        // given
        Device device = 기본_기기_빌더().build();
        entities.persist(device);
        entities.persist(기본_카드_빌더().deviceId(device.getId()).build());
        entities.flush();
        var duplicate = 기본_카드_빌더().deviceId(device.getId()).combinationIndex(1).build();

        // when / then
        assertThatThrownBy(() -> {
            entities.persist(duplicate);
            entities.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    void 날짜가_달라도_같은_기기_감정_회차에서는_조합을_중복_소비하지_못한다() {
        // given
        Device device = 기본_기기_빌더().build();
        entities.persist(device);
        entities.persist(기본_카드_빌더().deviceId(device.getId()).build());
        entities.flush();
        var duplicate = 기본_카드_빌더().deviceId(device.getId()).cardDate(LocalDate.of(2026, 10, 23)).build();

        // when / then
        assertThatThrownBy(() -> {
            entities.persist(duplicate);
            entities.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    void 다른_기기_감정_회차는_같은_조합을_독립적으로_저장한다() {
        // given
        Device owner = 기본_기기_빌더().build();
        Device other = 기본_기기_빌더().build();
        entities.persist(owner);
        entities.persist(other);

        // when
        entities.persist(기본_카드_빌더().deviceId(owner.getId()).build());
        entities.persist(기본_카드_빌더().deviceId(other.getId()).build());
        entities.persist(기본_카드_빌더().deviceId(owner.getId()).state(EmotionState.EXHAUSTED).build());
        entities.persist(기본_카드_빌더().deviceId(owner.getId()).cycle(2)
                .cardDate(LocalDate.of(2026, 10, 23)).build());
        entities.flush();
        entities.clear();

        // then
        assertThat(entities.createQuery("SELECT card FROM CollectionCard card", CollectionCard.class).getResultList())
                .hasSize(4).allSatisfy(card -> assertThat(card.getCombinationIndex()).isZero());
    }

    @Test
    void 기기_감정별_추첨_상태는_하나만_저장한다() {
        // given
        Device device = 기본_기기_빌더().build();
        entities.persist(device);
        entities.persist(기본_추첨_상태_빌더().deviceId(device.getId()).build());
        entities.flush();

        // when / then
        assertThatThrownBy(() -> {
            entities.persist(기본_추첨_상태_빌더().deviceId(device.getId()).build());
            entities.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    void 카드_조합에서_생성한_번호로_캐릭터와_도장_쌍을_참조한다() {
        // given
        Device device = 기본_기기_빌더().build();
        entities.persist(device);
        var card = 기본_카드_빌더().deviceId(device.getId()).combinationIndex(37).build();

        // when
        entities.persist(card);
        entities.flush();

        // then
        assertThat(jdbc.sql("SELECT character_number FROM collection_cards WHERE id = :id")
                .param("id", card.getId()).query(Integer.class).single()).isEqualTo(3);
        assertThat(jdbc.sql("SELECT stamp_number FROM collection_cards WHERE id = :id")
                .param("id", card.getId()).query(Integer.class).single()).isEqualTo(6);
        assertThatThrownBy(() -> jdbc.sql("""
                DELETE FROM collection_stamp_assets
                WHERE card_version = 'v1' AND state = 'ANGRY' AND asset_number = 6
                """).update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"combination_index = -1", "combination_index = 256", "cycle = 0",
            "state = 'UNKNOWN'", "record_status = 'UNKNOWN'", "card_version = ' '", "card_version = ''",
            "device_id = 9223372036854775807", "card_version = 'missing-v1'"})
    void DB도_유효하지_않은_카드_값과_없는_소유_기기를_거절한다(String invalidAssignment) {
        // given
        Device device = 기본_기기_빌더().build();
        entities.persist(device);
        var card = 기본_카드_빌더().deviceId(device.getId()).build();
        entities.persist(card);
        entities.flush();

        // when / then
        assertThatThrownBy(() -> jdbc.sql("UPDATE collection_cards SET " + invalidAssignment + " WHERE id = :id")
                .param("id", card.getId()).update()).isInstanceOf(DataIntegrityViolationException.class);
    }
}
