package com.pheeeew.appconfig.presentation.dto;

import com.pheeeew.appconfig.application.dto.AppConfigResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record AppConfigResponse(
        @Schema(description = "플랫폼별 양의 변경 번호. 실제 변경과 복구마다 1 증가하며 과거 번호를 재사용하지 않습니다.",
                minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        long revision,

        @Schema(description = "앱의 일일 프레스 순위 표시 여부. 서버 프레스 입력·집계·조회·알림 상태를 변경하지 않습니다.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean dailyPressRankingEnabled,

        @Schema(description = "일반 텍스트 공지. 빈 문자열은 공지 없음이며 줄바꿈과 공백을 보존합니다. 최대 200 Unicode 코드 포인트입니다.",
                maxLength = 200, requiredMode = Schema.RequiredMode.REQUIRED, example = "")
        String noticeMessage,

        @Schema(description = "서버에서 등록·변경한 UTC 시각. 앱 수신 시각이나 DB COMMIT 완료 시각과는 다릅니다.",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-29T03:00:00Z")
        Instant updatedAt
) {

    public static AppConfigResponse from(AppConfigResult result) {
        return new AppConfigResponse(result.revision(), result.dailyPressRankingEnabled(),
                result.noticeMessage(), result.updatedAt());
    }
}
