package com.pheeeew.collection.application;

import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_ASSETS_NOT_READY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.collection.domain.repository.CollectionAssetRepository;
import com.pheeeew.collection.exception.CollectionException;
import com.pheeeew.collection.infra.CollectionAssetConfiguration;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.support.PostgisDataJpaTest;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

@PostgisDataJpaTest
@Import({CollectionAssetConfiguration.class, CollectionAssetRepository.class})
@TestPropertySource(properties = {
        "pheeeew.collection.asset-base-url=https://assets.example/development/collection-cards/",
        "pheeeew.collection.card-version=v1"
})
class CollectionAssetServiceIntegrationTest {

    private static final String BASE_URL = "https://assets.example/development/collection-cards/";

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private CollectionAssetRepository assetRepository;

    @Autowired
    private CollectionAssetService service;

    @Test
    void 초기_자산으로_완성된_버전만_신규_발급에_사용한다() {
        // given

        // when / then
        assertThat(service.activeVersion()).isEqualTo("v1");
        jdbc.sql("DELETE FROM collection_character_assets WHERE card_version = 'v1' AND state = 'ANGRY' AND asset_number = 16")
                .update();
        assertThatThrownBy(service::activeVersion).isInstanceOfSatisfying(CollectionException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo(COLLECTION_ASSETS_NOT_READY));
    }

    @ParameterizedTest
    @CsvSource({"0,01,01", "15,01,16", "16,02,01", "37,03,06", "255,16,16"})
    void 조합의_캐릭터와_도장_쌍을_DB에서_찾아_URL로_반환한다(int combination, String character, String stamp) {
        // given

        // when / then
        for (EmotionState state : EmotionState.values()) {
            var images = service.findImages("v1", state, combination);
            String emotion = state.name().toLowerCase(Locale.ROOT);
            assertThat(images.characterUrl()).isEqualTo(BASE_URL + "v1/characters/" + emotion + "/" + character + ".png");
            assertThat(images.roundStampUrl()).isEqualTo(BASE_URL + "v1/stamps-round/" + emotion + "/" + stamp + ".png");
            assertThat(images.straightStampUrl()).isEqualTo(BASE_URL + "v1/stamps-straight/" + emotion + "/" + stamp + ".png");
        }
    }

    @Test
    void 활성_버전이_달라도_기존_카드에_저장된_버전의_이미지는_조회한다() {
        // given
        var service = new CollectionAssetService(assetRepository, BASE_URL, "v2");

        // when
        var images = service.findImages("v1", EmotionState.ANGRY, 37);

        // then
        assertThat(images.characterUrl()).isEqualTo(BASE_URL + "v1/characters/angry/03.png");
        assertThat(images.roundStampUrl()).isEqualTo(BASE_URL + "v1/stamps-round/angry/06.png");
        assertThat(images.straightStampUrl()).isEqualTo(BASE_URL + "v1/stamps-straight/angry/06.png");
        assertThatThrownBy(service::activeVersion).isInstanceOf(CollectionException.class);
    }

    @Test
    void 필요한_도장_세트가_없으면_다른_세트로_대체하지_않는다() {
        // given
        jdbc.sql("DELETE FROM collection_stamp_assets WHERE card_version = 'v1' AND state = 'ANGRY' AND asset_number = 6")
                .update();

        // when / then
        assertThatThrownBy(() -> service.findImages("v1", EmotionState.ANGRY, 37))
                .isInstanceOfSatisfying(CollectionException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(COLLECTION_ASSETS_NOT_READY));
        assertThatThrownBy(service::activeVersion).isInstanceOf(CollectionException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "http://assets.example/", "https://assets.example/cards", "https://assets.example/a/../cards/",
            "https://user:password@assets.example/", "https://assets.example/?query=value", "https://assets.example/#fragment"})
    void 안전한_이미지_기본_주소가_없으면_발급과_이미지_조회를_차단한다(String baseUrl) {
        // given
        var service = new CollectionAssetService(assetRepository, baseUrl, "v1");

        // when / then
        assertThatThrownBy(service::activeVersion).isInstanceOf(CollectionException.class);
        assertThatThrownBy(() -> service.findImages("v1", EmotionState.ANGRY, 37))
                .isInstanceOf(CollectionException.class);
    }

    @Test
    void 신규_발급_버전이_미설정이어도_기존_카드_이미지는_조회한다() {
        // given
        var service = new CollectionAssetService(assetRepository, BASE_URL, "");

        // when / then
        assertThatThrownBy(service::activeVersion).isInstanceOf(CollectionException.class);
        assertThat(service.findImages("v1", EmotionState.ANGRY, 37).characterUrl())
                .isEqualTo(BASE_URL + "v1/characters/angry/03.png");
    }
}
