package com.pheeeew.collection.domain;

import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CollectionCardTest {

    @Test
    void 수령_카드는_추첨_결과와_카드_버전을_보존한_OPEN_기록으로_만든다() {
        // given / when
        CollectionCard card = 기본_카드_빌더().cycle(3).combinationIndex(255).build();

        // then
        assertThat(card.getPublicId()).isNotNull();
        assertThat(card.getCycle()).isEqualTo(3);
        assertThat(card.getCombinationIndex()).isEqualTo(255);
        assertThat(card.getCardVersion()).isEqualTo("test-card-v1");
        assertThat(card.getRecordStatus()).isEqualTo(CollectionRecordStatus.OPEN);
    }

    @Test
    void 유효하지_않은_조합과_회차로_카드를_만들_수_없다() {
        // given / when / then
        assertThatThrownBy(() -> 기본_카드_빌더().combinationIndex(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> 기본_카드_빌더().combinationIndex(256).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> 기본_카드_빌더().cycle(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
