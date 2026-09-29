package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record EmotionUpdateRequest(
        @NotNull EmotionState state,
        @NotNull EmotionContentType contentType,
        @Schema(description = "MEMO에서만 전달합니다. 앞뒤 공백 제거 후 최대 200 Unicode codepoint이며 null·공백은 내용 없음입니다.") String memo,
        @Schema(description = "AUDIO에서 새 녹음으로 교체할 때 전달합니다. 생략하거나 null이면 기존 녹음을 유지합니다. 기존 녹음도 없으면 400입니다. NONE/MEMO에서는 null입니다.") String audioUploadId,
        @Schema(description = "수정 후 사용할 그룹의 groupId. 생략하거나 null이면 그룹 스탬프를 제거합니다. 현재와 다른 그룹은 소속 검증을 거칩니다.") UUID groupId
) {

    @AssertTrue(message = "내용 유형과 메모·녹음 식별자가 올바르지 않거나 메모가 200자를 초과합니다.")
    @Schema(hidden = true)
    public boolean isContentValid() {
        if (contentType == EmotionContentType.AUDIO && audioUploadId == null) {
            return memo == null;
        }
        EmotionContentRequest content = EmotionContentRequest.of(contentType, memo, audioUploadId);
        return content.isContentCombinationValid() && content.isMemoLengthValid();
    }
}
