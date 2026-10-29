package com.pheeeew.emotion.presentation.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

@Schema(name = "EmotionV4UpdateRequest")
public record EmotionV4UpdateRequest(
        @NotNull @Schema(description = "등록된 감정과 같은 값을 전달합니다. 감정 종류 변경은 400(EMOTION-015)으로 거절합니다.") EmotionState state,
        @NotNull @Schema(description = "MEMO 또는 AUDIO만 허용합니다. NONE은 허용하지 않습니다.") EmotionContentType contentType,
        @Schema(description = "MEMO에서만 전달합니다. 앞뒤 공백 제거 후 최대 200 Unicode codepoint이며 null·빈 문자열·공백뿐인 메모는 허용하지 않습니다. AUDIO에서는 생략하거나 null로 둡니다.") String memo,
        @Schema(description = "AUDIO에서 새 녹음으로 교체할 때 전달하는 업로드 완료된 녹음의 uploadId입니다. 생략하거나 null이면 기존 녹음을 유지하며, 기존 녹음도 없으면 400입니다. 공백은 허용하지 않습니다. MEMO에서는 생략하거나 null로 둡니다. S3 객체 키나 URL이 아닙니다.") String audioUploadId
) {

    @AssertTrue(message = "내용 유형과 메모·녹음 식별자가 올바르지 않거나 메모가 200자를 초과합니다.")
    @Schema(hidden = true)
    public boolean isContentValid() {
        if (contentType == EmotionContentType.AUDIO && audioUploadId == null) {
            return memo == null;
        }
        EmotionContentRequest content = EmotionContentRequest.of(contentType, memo, audioUploadId);
        return content.isRequiredContentValid();
    }

    @JsonAnySetter
    public void rejectGroupId(String name, JsonNode value) {
        if ("groupId".equals(name)) {
            throw new IllegalArgumentException("v4에서는 groupId를 전달할 수 없습니다.");
        }
    }
}
