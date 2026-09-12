package com.pheeeew.device.presentation.dto;

import com.pheeeew.device.domain.DevicePlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record DeviceAttestationRequest(
        @NotNull(message = "플랫폼은 필수입니다.")
        @Schema(
                description = "기기 플랫폼입니다. `ANDROID` 또는 `IOS`만 보낼 수 있습니다.",
                example = "ANDROID"
        )
        DevicePlatform platform,

        @Schema(
                description = """
                        무결성 증명 토큰입니다. 저장하지 않습니다.

                        생략하거나 `null`로 두면 검증하지 않고 등록합니다. 보내면 서버가 실제로 검증하며,
                        검증에 실패하면 403이고 `challenge`를 이미 썼거나 만료되었으면 400입니다.

                        `ANDROID`만 검증할 수 있습니다. `IOS`로 보내면 403입니다.
                        앱 전환이 끝나면 `ANDROID`에서 필수가 됩니다.
                        """,
                nullable = true
        )
        String token,

        @Schema(
                description = """
                        증명 토큰을 만들 때 쓴 challenge입니다. 저장하지 않습니다.

                        `token`을 보내면 필수입니다. 없거나 이미 썼거나 만료된 값이면 서버는 복호화하지 않고 400을 반환합니다.
                        `token`을 생략하면 이 값도 보내지 않습니다.

                        복호화한 토큰 안의 값과 다르면 403입니다. `challenge`를 실제로 소모하는 기준은 토큰 안의 값입니다.
                        """,
                example = "PjONwTh56rDaOsphVQQeqQcPyQtLZL-reX5Us2xD2SM",
                nullable = true
        )
        String challenge,

        @Schema(
                description = """
                        무결성 증명 키 식별자입니다.

                        현재 버전에서는 검증하지 않으며 저장하지도 않습니다. 보낼 값이 없으면 `null`로 두거나 생략합니다.
                        """,
                nullable = true
        )
        String keyId
) {

    @Override
    public String toString() {
        return "DeviceAttestationRequest[platform=" + platform
                + ", token=<redacted>, challenge=<redacted>, keyId=<redacted>]";
    }
}
