package com.pheeeew.pressevent.presentation.dto;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressevent.domain.PressEventCounts;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record PressEventAttemptRequest(
        @NotNull
        @Schema(description = "도전마다 생성하는 UUID. 같은 도전을 재시도할 때 유지합니다.")
        UUID requestId,

        @NotNull
        @JsonDeserialize(using = CountsDeserializer.class)
        @Schema(
                description = "다섯 감정 키가 모두 필요합니다. 각 값은 0 이상인 JSON 정수이며 합계는 최대 400입니다.",
                example = """
                        {"FRUSTRATED":0,"IRRITATED":0,"EXHAUSTED":3,"DISCOURAGED":0,"ANGRY":9}
                        """
        )
        Map<EmotionState, Integer> counts
) {

    public PressEventCounts validatedCounts() {
        return PressEventCounts.from(counts);
    }

    public static class CountsDeserializer extends ValueDeserializer<Map<EmotionState, Integer>> {

        @Override
        public Map<EmotionState, Integer> deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.START_OBJECT)) {
                return context.reportInputMismatch(Map.class, "counts는 감정별 횟수를 담은 객체여야 합니다.");
            }
            Map<EmotionState, Integer> counts = new EnumMap<>(EmotionState.class);
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                String name = parser.currentName();
                EmotionState emotion;
                try {
                    emotion = EmotionState.valueOf(name);
                } catch (IllegalArgumentException exception) {
                    return context.reportInputMismatch(Map.class, "지원하지 않는 감정 식별자입니다.");
                }
                if (counts.containsKey(emotion) || parser.nextToken() != JsonToken.VALUE_NUMBER_INT) {
                    return context.reportInputMismatch(Map.class, "각 감정의 정수 횟수를 한 번씩 지정해야 합니다.");
                }
                counts.put(emotion, parser.getIntValue());
            }
            if (!parser.hasToken(JsonToken.END_OBJECT)) {
                return context.reportInputMismatch(Map.class, "counts 객체가 올바르게 닫히지 않았습니다.");
            }
            return counts;
        }
    }
}
