package com.pheeeew.emotion.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.emotion.domain.EmotionContent;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class EmotionContentRequestTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"contentType\":\"NONE\"}",
            "{\"contentType\":\"MEMO\",\"memo\":\"메모\"}",
            "{\"contentType\":\"MEMO\"}",
            "{\"contentType\":\"AUDIO\",\"audioUploadId\":\"upload-id\"}"
    })
    void 유형과_내용이_일치하는_요청을_허용한다(String json) throws Exception {
        // given
        EmotionContentRequest request = JSON_MAPPER.readValue(json, EmotionContentRequest.class);

        // when / then
        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"contentType\":\"NONE\",\"memo\":\"메모\"}",
            "{\"contentType\":\"NONE\",\"audioUploadId\":\"upload-id\"}",
            "{\"contentType\":\"MEMO\",\"audioUploadId\":\"upload-id\"}",
            "{\"contentType\":\"AUDIO\"}",
            "{\"contentType\":\"AUDIO\",\"audioUploadId\":\"\"}",
            "{\"contentType\":\"AUDIO\",\"audioUploadId\":\"   \"}",
            "{\"contentType\":\"AUDIO\",\"memo\":\"메모\",\"audioUploadId\":\"upload-id\"}"
    })
    void 유형이_없거나_내용과_일치하지_않으면_거부한다(String json) throws Exception {
        // given
        EmotionContentRequest request = JSON_MAPPER.readValue(json, EmotionContentRequest.class);

        // when / then
        assertThat(validator.validate(request)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t\n"})
    void 빈_메모는_허용하고_도메인에서_내용_없음으로_정규화한다(String memo) {
        // given
        EmotionContentRequest request = JSON_MAPPER.convertValue(
                Map.of("contentType", "MEMO", "memo", memo), EmotionContentRequest.class);

        // when / then
        assertThat(validator.validate(request)).isEmpty();
        assertThat(EmotionContent.builder().memo(request.memo()).build().getMemo()).isNull();
    }

    @ParameterizedTest
    @MethodSource("memoLengths")
    void 공백_제거_후_유니코드_코드포인트로_메모_길이를_검증한다(String character, int length, boolean valid) {
        // given
        EmotionContentRequest request = JSON_MAPPER.convertValue(
                Map.of("contentType", "MEMO", "memo", "  " + character.repeat(length) + "  "),
                EmotionContentRequest.class);

        // when / then
        assertThat(validator.validate(request).isEmpty()).isEqualTo(valid);
    }

    private static Stream<Arguments> memoLengths() {
        return Stream.of(
                Arguments.of("가", 200, true),
                Arguments.of("가", 201, false),
                Arguments.of("😀", 200, true),
                Arguments.of("😀", 201, false)
        );
    }
}
