package com.pheeeew.device.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record NicknameAvailabilityResponse(
        @Schema(description = "조회 시점의 닉네임 사용 가능 여부입니다. 닉네임을 예약하지 않습니다.", example = "true")
        boolean available
) {

    public static NicknameAvailabilityResponse from(boolean available) {
        return new NicknameAvailabilityResponse(available);
    }
}
