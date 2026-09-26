package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record EmotionCreateRequest(
        @NotNull @Schema(description = "등록 시도 식별자. 재시도에는 같은 UUID를 사용합니다.") UUID requestId,
        @NotNull EmotionState state,
        @NotNull @DecimalMin("-180") @DecimalMax("180")
        @Schema(description = "사용자가 선택한 WGS84 경도. 서버에서 이동하지 않습니다.") Double longitude,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("0") @DecimalMax(value = "360", inclusive = false) Double rotationDegrees,
        @NotNull @Schema(description = "NONE은 내용 없음, MEMO는 메모, AUDIO는 녹음입니다.") EmotionContentType contentType,
        @Schema(description = "MEMO에서만 전달합니다. 앞뒤 공백 제거 후 최대 200 Unicode codepoint이며 null·공백은 내용 없음입니다. NONE/AUDIO에서는 생략하거나 null로 둡니다.") String memo,
        @Schema(description = "AUDIO에서 필수인 업로드 완료된 녹음의 uploadId입니다. 공백은 허용하지 않습니다. NONE/MEMO에서는 생략하거나 null로 둡니다. S3 객체 키나 URL이 아닙니다.") String audioUploadId,
        @Schema(description = "선택한 소속 그룹의 groupId. 생략하거나 null이면 그룹 스탬프 없이 등록합니다.") UUID groupId
) {

    @AssertTrue(message = "내용 유형과 메모·녹음 식별자가 올바르지 않거나 메모가 200자를 초과합니다.")
    @Schema(hidden = true)
    public boolean isContentValid() {
        EmotionContentRequest content = EmotionContentRequest.of(contentType, memo, audioUploadId);
        return content.isContentCombinationValid() && content.isMemoLengthValid();
    }
}
