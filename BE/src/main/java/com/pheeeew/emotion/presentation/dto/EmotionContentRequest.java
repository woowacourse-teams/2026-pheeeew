package com.pheeeew.emotion.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record EmotionContentRequest(
        @NotNull(message = "내용 유형은 필수입니다.")
        @Schema(description = "NONE은 내용 없음, MEMO는 메모, AUDIO는 녹음입니다.")
        EmotionContentType contentType,

        @Schema(
                description = """
                        MEMO일 때 전달하는 메모입니다. 앞뒤 공백을 제거한 뒤 최대 200 Unicode codepoint입니다.
                        생략하거나 공백뿐이면 내용 없음으로 처리합니다. NONE과 AUDIO에서는 생략하거나 null로 둡니다.
                        """,
                nullable = true
        )
        String memo,

        @Schema(
                description = """
                        AUDIO일 때 필수인 업로드 준비 응답의 uploadId입니다. 업로드를 완료한 뒤 전달합니다.
                        파일 키나 재생 URL이 아닙니다. NONE과 MEMO에서는 생략하거나 null로 둡니다.
                        """,
                nullable = true
        )
        String audioUploadId
) {

    @AssertTrue(message = "내용 유형에 맞는 메모 또는 녹음 업로드 식별자를 전달해야 합니다.")
    @Schema(hidden = true)
    public boolean isContentCombinationValid() {
        if (contentType == null) {
            return true;
        }

        return switch (contentType) {
            case NONE -> memo == null && audioUploadId == null;
            case MEMO -> audioUploadId == null;
            case AUDIO -> memo == null && audioUploadId != null && !audioUploadId.isBlank();
        };
    }

    @AssertTrue(message = "메모는 200자를 초과할 수 없습니다.")
    @Schema(hidden = true)
    public boolean isMemoLengthValid() {
        if (memo == null) {
            return true;
        }

        String normalizedMemo = memo.strip();
        return normalizedMemo.codePointCount(0, normalizedMemo.length()) <= 200;
    }
}
