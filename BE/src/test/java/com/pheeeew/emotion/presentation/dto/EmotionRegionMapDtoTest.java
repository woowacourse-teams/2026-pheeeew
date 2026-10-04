package com.pheeeew.emotion.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.emotion.application.dto.EmotionRegionMapItemView;
import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.region.domain.Region;
import com.pheeeew.region.domain.RegionLevel;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class EmotionRegionMapDtoTest {

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
    @CsvSource({"SIDO,-180,-90,180,90", "SIGUNGU,170,37,-170,39", "EMD,126.9,37.5,127.1,37.6"})
    void 계층과_그룹을_받고_세계_경계와_날짜변경선_영역을_허용한다(RegionLevel level, double west, double south, double east, double north) {
        // given
        UUID groupId = UUID.randomUUID();
        var values = parameters();
        values.putAll(Map.of("level", level, "minLongitude", west, "minLatitude", south,
                "maxLongitude", east, "maxLatitude", north, "groupId", groupId));
        var request = JSON_MAPPER.convertValue(values, EmotionRegionMapRequest.class);

        // when / then
        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.toBounds()).isEqualTo(EmotionSearchBounds.of(west, south, east, north));
        assertThat(request.level()).isEqualTo(level);
        assertThat(request.groupId()).isEqualTo(groupId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"minLongitude", "minLatitude", "maxLongitude", "maxLatitude", "level"})
    void bbox와_계층의_누락을_거부하고_그룹은_선택적으로_받는다(String field) {
        // given
        var values = parameters();
        values.remove(field);
        var request = JSON_MAPPER.convertValue(values, EmotionRegionMapRequest.class);

        // when / then
        assertThat(validator.validate(request)).extracting(value -> value.getPropertyPath().toString()).containsExactly(field);
        assertThat(request.groupId()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "minLongitude,-180.1,minLongitude", "minLongitude,180.1,minLongitude",
            "maxLongitude,-180.1,maxLongitude", "maxLongitude,180.1,maxLongitude",
            "minLatitude,-90.1,minLatitude", "minLatitude,90.1,minLatitude",
            "maxLatitude,-90.1,maxLatitude", "maxLatitude,90.1,maxLatitude",
            "minLongitude,NaN,minLongitude", "maxLongitude,Infinity,maxLongitude",
            "minLatitude,-Infinity,minLatitude", "maxLatitude,NaN,maxLatitude",
            "minLongitude,127.1,longitudeRangeValid", "maxLongitude,126.9,longitudeRangeValid",
            "minLatitude,37.6,latitudeRangeValid", "minLatitude,38,latitudeRangeValid",
            "maxLatitude,37.5,latitudeRangeValid", "maxLatitude,37.4,latitudeRangeValid"
    })
    void 좌표_범위와_유한값_및_영역의_너비와_높이를_검증한다(String field, double value, String violation) {
        // given
        var values = parameters();
        values.put(field, value);
        var request = JSON_MAPPER.convertValue(values, EmotionRegionMapRequest.class);

        // when / then
        assertThat(validator.validate(request)).extracting(error -> error.getPropertyPath().toString()).contains(violation);
    }

    @ParameterizedTest
    @CsvSource({"SIDO,11,,ANGRY", "SIGUNGU,11010,11,IRRITATED", "EMD,11010530,11010,"})
    void 코드와_고정_Point_및_누적_요약을_Feature로_직렬화한다(RegionLevel level, String code, String parentCode, EmotionState state) {
        // given: 실제 SGIS 표시점·개수가 아닌 직렬화 검증용 값이다.
        var region = Region.of(code, level, "검증용 지역", parentCode, 127, 38);
        var view = EmotionRegionMapItemView.of(region, RegionEmotionSummary.of(3_000_000_000L, state));

        // when
        String actual = JSON_MAPPER.writeValueAsString(EmotionRegionMapResponse.from(view));

        // then: 지역 ID는 문자열이고, 부모·대표 감정의 null도 그대로 전달한다.
        String expected = """
                {"type":"Feature","id":"%s","geometry":{"type":"Point","coordinates":[127.0,38.0]},
                 "properties":{"level":"%s","name":"검증용 지역","parentCode":%s,
                 "totalCount":3000000000,"representativeState":%s}}
                """.formatted(code, level, parentCode == null ? "null" : "\"" + parentCode + "\"",
                        state == null ? "null" : "\"" + state.name() + "\"");
        assertThat(JSON_MAPPER.readTree(actual)).isEqualTo(JSON_MAPPER.readTree(expected));
    }

    private Map<String, Object> parameters() {
        return new HashMap<>(Map.of("minLongitude", 126.9, "minLatitude", 37.5,
                "maxLongitude", 127.1, "maxLatitude", 37.6, "level", RegionLevel.EMD));
    }
}
