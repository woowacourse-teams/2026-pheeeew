package com.pheeeew.appversion.presentation.dto;

import com.pheeeew.appversion.application.dto.AppVersionResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record AppVersionResponse(
        @Schema(description = "이 버전 미만이면 강제 업데이트가 필요한 최소 지원 버전입니다.", example = "1.0.0")
        String minSupportedVersion,

        @Schema(description = "스토어에서 설치 가능한 최신 권장 버전입니다.", example = "1.1.0")
        String latestVersion,

        @Schema(description = "해당 플랫폼의 앱 업데이트를 위한 스토어 이동 URL입니다.")
        String storeUrl
) {

    public static AppVersionResponse from(AppVersionResult result) {
        return new AppVersionResponse(result.minSupportedVersion(), result.latestVersion(), result.storeUrl());
    }
}
