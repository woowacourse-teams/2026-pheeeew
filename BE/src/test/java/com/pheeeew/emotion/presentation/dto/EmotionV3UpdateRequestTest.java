package com.pheeeew.emotion.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class EmotionV3UpdateRequestTest {

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
            "{\"state\":\"ANGRY\",\"contentType\":\"MEMO\",\"memo\":\" 메모 \"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"AUDIO\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"AUDIO\",\"audioUploadId\":\"upload-id\"}"
    })
    void 유효한_메모와_녹음_유지_또는_교체_요청을_허용한다(String json) {
        // given
        EmotionV3UpdateRequest request = JSON_MAPPER.readValue(json, EmotionV3UpdateRequest.class);

        // when / then
        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"state\":\"ANGRY\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"NONE\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"NONE\",\"memo\":\"메모\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"NONE\",\"audioUploadId\":\"upload-id\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"MEMO\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"MEMO\",\"memo\":\"\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"MEMO\",\"memo\":\" \\t\\n　\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"MEMO\",\"memo\":\"메모\",\"audioUploadId\":\"upload-id\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"MEMO\",\"memo\":\"메모\",\"audioUploadId\":\"\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"AUDIO\",\"audioUploadId\":\"\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"AUDIO\",\"audioUploadId\":\"   \"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"AUDIO\",\"memo\":\"메모\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"AUDIO\",\"memo\":\"\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"AUDIO\",\"memo\":\"메모\",\"audioUploadId\":\"upload-id\"}"
    })
    void 내용이_없거나_유형과_필드가_일치하지_않으면_거부한다(String json) {
        // given
        EmotionV3UpdateRequest request = JSON_MAPPER.readValue(json, EmotionV3UpdateRequest.class);

        // when / then
        assertThat(validator.validate(request)).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("contentValid"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"state\":\"ANGRY\",\"contentType\":\"NONE\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"MEMO\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"MEMO\",\"memo\":\"\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"MEMO\",\"memo\":\"   \"}"
    })
    void v1은_내용_없음을_허용하고_v3은_거부한다(String json) {
        // given
        EmotionUpdateRequest legacy = JSON_MAPPER.readValue(json, EmotionUpdateRequest.class);
        EmotionV3UpdateRequest request = JSON_MAPPER.readValue(json, EmotionV3UpdateRequest.class);

        // when / then
        assertThat(validator.validate(legacy)).isEmpty();
        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void 감정_상태는_필수다() {
        // given
        EmotionV3UpdateRequest request = JSON_MAPPER.convertValue(
                Map.of("contentType", "MEMO", "memo", "메모"), EmotionV3UpdateRequest.class);

        // when / then
        assertThat(validator.validate(request)).singleElement().satisfies(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("state"));
    }

}
