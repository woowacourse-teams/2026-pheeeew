package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record EmotionV3CreateRequest(
        @NotNull @Schema(description = "등록 시도 식별자. 재시도에는 같은 UUID를 사용합니다.") UUID requestId,
        @NotNull EmotionState state,
        @NotNull @DecimalMin("-180") @DecimalMax("180")
        @Schema(description = "사용자가 선택한 WGS84 경도. 서버에서 이동하지 않습니다.") Double longitude,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("0") @DecimalMax(value = "360", inclusive = false) Double rotationDegrees,
        @NotNull @Schema(description = "MEMO 또는 AUDIO만 허용합니다. NONE은 허용하지 않습니다.") EmotionContentType contentType,
        @Schema(description = "MEMO에서만 전달합니다. 앞뒤 공백 제거 후 최대 200 Unicode codepoint이며 null·빈 문자열·공백뿐인 메모는 허용하지 않습니다. AUDIO에서는 생략하거나 null로 둡니다.") String memo,
        @Schema(description = "AUDIO에서 필수인 업로드 완료된 녹음의 uploadId입니다. 공백은 허용하지 않습니다. MEMO에서는 생략하거나 null로 둡니다. S3 객체 키나 URL이 아닙니다.") String audioUploadId,
        @Schema(description = "선택한 소속 그룹의 groupId. 생략하거나 null이면 그룹 스탬프 없이 등록합니다.") UUID groupId,
        @JsonDeserialize(using = AnonymousDeserializer.class)
        @Schema(description = "JSON boolean으로 전달합니다. true는 익명, false는 기명입니다. 생략하거나 null이면 익명으로 등록합니다. 기명 등록에는 기기 닉네임 설정이 필요합니다.",
                defaultValue = "true", nullable = true) Boolean anonymous
) {

    @AssertTrue(message = "내용 유형과 메모·녹음 식별자가 올바르지 않거나 메모가 200자를 초과합니다.")
    @Schema(hidden = true)
    public boolean isContentValid() {
        EmotionContentRequest content = EmotionContentRequest.of(contentType, memo, audioUploadId);
        return content.isRequiredContentValid();
    }

    public static class AnonymousDeserializer extends ValueDeserializer<Boolean> {

        @Override
        public Boolean deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.hasToken(JsonToken.VALUE_TRUE)) {
                return true;
            }
            if (parser.hasToken(JsonToken.VALUE_FALSE)) {
                return false;
            }
            return (Boolean) context.handleUnexpectedToken(Boolean.class, parser);
        }
    }
}
