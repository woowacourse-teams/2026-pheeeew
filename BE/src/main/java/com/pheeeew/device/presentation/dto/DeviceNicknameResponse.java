package com.pheeeew.device.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record DeviceNicknameResponse(
        @Schema(description = "현재 기기 닉네임입니다. 미설정 기기는 null을 반환합니다.",
                example = "스타크", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname
) {

    public static DeviceNicknameResponse from(String nickname) {
        return new DeviceNicknameResponse(nickname);
    }
}
