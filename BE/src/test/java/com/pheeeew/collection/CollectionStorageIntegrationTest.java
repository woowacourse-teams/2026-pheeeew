package com.pheeeew.collection;

import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_빌더;
import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_추첨_상태_빌더;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.collection.domain.CollectionCard;
import com.pheeeew.collection.domain.CollectionDrawState;
import com.pheeeew.collection.domain.CollectionRecordStatus;
import com.pheeeew.device.domain.Device;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.Random;
import java.util.Set;
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
    void 카드와_추첨_상태의_회차_조합_버전_시각을_저장하고_다시_읽는다() {
        // given
        Device device = 기본_기기_빌더().build();
        entities.persist(device);
        var drawState = 기본_추첨_상태_빌더().deviceId(device.getId()).build();
        var drawn = drawState.draw(Set.of(), new Random(840));
        var card = 기본_카드_빌더().deviceId(device.getId()).cycle(drawn.cycle())
                .combinationIndex(drawn.combinationIndex()).build();

        // when
        entities.persist(drawState);
        entities.persist(card);
        entities.flush();
        entities.clear();
        var storedCard = entities.find(CollectionCard.class, card.getId());
        var storedState = entities.find(CollectionDrawState.class, drawState.getId());

        // then
        assertThat(storedCard.getPublicId()).isEqualTo(card.getPublicId());
        assertThat(storedCard.getDeviceId()).isEqualTo(device.getId());
        assertThat(storedCard.getCardDate()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(storedCard.getState()).isEqualTo(EmotionState.ANGRY);
        assertThat(storedCard.getCycle()).isEqualTo(1);
        assertThat(storedCard.getCombinationIndex()).isEqualTo(drawn.combinationIndex());
        assertThat(storedCard.getCardVersion()).isEqualTo("test-card-v1");
        assertThat(storedCard.getClaimedAt()).isEqualTo(card.getClaimedAt());
        assertThat(storedCard.getRecordStatus()).isEqualTo(CollectionRecordStatus.OPEN);
        assertThat(storedCard.getCreatedAt()).isNotNull();
        assertThat(storedCard.getUpdatedAt()).isNotNull();
        assertThat(storedState.getDeviceId()).isEqualTo(device.getId());
        assertThat(storedState.getState()).isEqualTo(EmotionState.ANGRY);
        assertThat(storedState.getCycle()).isEqualTo(1);
        assertThat(storedState.getLastCombinationIndex()).isEqualTo(drawn.combinationIndex());
        assertThat(storedState.getCreatedAt()).isNotNull();
    }

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

    @ParameterizedTest
    @ValueSource(strings = {"combination_index = -1", "combination_index = 256", "cycle = 0",
            "state = 'UNKNOWN'", "record_status = 'UNKNOWN'", "card_version = ' '", "card_version = ''",
            "device_id = 9223372036854775807"})
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
