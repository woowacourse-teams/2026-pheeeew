package com.pheeeew.device.presentation.dto;

import com.pheeeew.device.application.dto.DeviceChallengeResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record DeviceChallengeResponse(
        @Schema(
                description = """
                        무결성 증명 요청에 그대로 넣어 보낼 1회용 값입니다.

                        한 번 쓰이면 다시 쓸 수 없고 5분 뒤 만료됩니다. 앱이 값을 해석하거나 가공하지 않습니다.
                        """,
                example = "PjONwTh56rDaOsphVQQeqQcPyQtLZL-reX5Us2xD2SM"
        )
        String challenge,

        @Schema(description = "challenge 의 유효 시간(초)입니다. 서버가 정합니다.", example = "300")
        long expiresIn
) {

    public static DeviceChallengeResponse from(DeviceChallengeResult result) {
        return new DeviceChallengeResponse(result.challenge(), result.expiresIn());
    }

    @Override
    public String toString() {
        return "DeviceChallengeResponse[challenge=<redacted>, expiresIn=" + expiresIn + "]";
    }
}
