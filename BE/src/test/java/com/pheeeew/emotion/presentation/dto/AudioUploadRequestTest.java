package com.pheeeew.emotion.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class AudioUploadRequestTest {

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
    @ValueSource(longs = {1, 5_242_880})
    void mp4_녹음의_최소_최대_크기를_허용한다(long contentLength) {
        // given
        AudioUploadRequest request = JSON_MAPPER.convertValue(
                Map.of("contentType", "audio/mp4", "contentLength", contentLength), AudioUploadRequest.class);

        // when / then
        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 5_242_881, Long.MAX_VALUE})
    void 빈_파일과_음수_또는_최대_크기를_넘는_파일을_거부한다(long contentLength) {
        // given
        AudioUploadRequest request = JSON_MAPPER.convertValue(
                Map.of("contentType", "audio/mp4", "contentLength", contentLength), AudioUploadRequest.class);

        // when / then
        assertThat(validator.validate(request)).extracting(value -> value.getPropertyPath().toString())
                .containsExactly("contentLength");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "audio/webm", "video/mp4", "audio/mp4; codecs=mp4a.40.2", " audio/mp4 "})
    void 허용한_MIME_유형과_다른_값을_거부한다(String contentType) {
        // given
        Map<String, Object> body = new HashMap<>();
        body.put("contentType", contentType);
        body.put("contentLength", 1024);
        AudioUploadRequest request = JSON_MAPPER.convertValue(body, AudioUploadRequest.class);

        // when / then
        assertThat(validator.validate(request)).extracting(value -> value.getPropertyPath().toString())
                .containsExactly("contentType");
    }

    @Test
    void 필수_메타데이터가_없는_요청을_거부한다() {
        // given
        AudioUploadRequest request = JSON_MAPPER.readValue("{}", AudioUploadRequest.class);

        // when / then
        assertThat(validator.validate(request)).extracting(value -> value.getPropertyPath().toString())
                .containsExactlyInAnyOrder("contentType", "contentLength");
    }
}
