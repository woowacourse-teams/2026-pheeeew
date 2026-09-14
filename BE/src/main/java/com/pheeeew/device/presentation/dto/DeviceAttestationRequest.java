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

                        `ANDROID`는 Play Integrity 토큰을 그대로 보냅니다.

                        `IOS`는 App Attest attestation 객체를 **표준 Base64**로 인코딩해 보냅니다.
                        `Data.base64EncodedString()`의 결과이며, `-`와 `_`를 쓰는 Base64url이 아닙니다.
                        Base64url로 보내면 디코딩에 실패해 403입니다.
                        앱 전환이 끝나면 플랫폼마다 따로 필수가 됩니다.
                        """,
                nullable = true
        )
        String token,

        @Schema(
                description = """
                        증명 토큰을 만들 때 쓴 challenge입니다. 저장하지 않습니다.

                        `token`을 보내면 필수입니다. 없거나 이미 썼거나 만료된 값이면 400을 반환합니다.
                        `token`을 생략하면 이 값도 보내지 않습니다.

                        `ANDROID`는 이 값을 복호화 전 사전 조회에만 씁니다. 소모 기준은 복호화한 토큰 안의 값이고,
                        두 값이 다르면 403이며 challenge는 남습니다.

                        `IOS`는 attestation 객체를 해석한 뒤, 암호 검증을 시작하기 전에 이 값을 소모합니다.
                        형식 오류나 `keyId` 누락으로 떨어지면 소모되지 않지만, **인증서 체인 검증부터 뒤에서 실패하면
                        challenge는 사라지므로** 다시 시도하려면 새로 발급받아야 합니다.
                        """,
                example = "PjONwTh56rDaOsphVQQeqQcPyQtLZL-reX5Us2xD2SM",
                nullable = true
        )
        String challenge,

        @Schema(
                description = """
                        무결성 증명 키 식별자입니다. 저장하지 않습니다.

                        `IOS`가 `token`을 보낼 때 필수입니다. `generateKey`가 돌려준 값을 그대로 보냅니다.
                        Base64로 디코딩한 32바이트가 attestation 객체의 공개 키 해시와 달라지면 403입니다.

                        `ANDROID`는 쓰지 않습니다. 생략하거나 `null`로 둡니다.
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
