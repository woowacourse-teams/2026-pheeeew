package com.pheeeew.device.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.device.presentation.dto.AccessTokenReissueRequest;
import com.pheeeew.device.presentation.dto.AccessTokenResponse;
import com.pheeeew.device.presentation.dto.DeviceChallengeResponse;
import com.pheeeew.device.presentation.dto.DeviceCreateRequest;
import com.pheeeew.device.presentation.dto.DeviceTokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@Tag(name = "기기", description = "기기 등록과 토큰 발급 API")
public interface DeviceControllerApi {

    @Operation(
            summary = "기기 등록",
            description = """
                    ### 무엇을 발급받나요

                    - 등록에 성공하면 access token과 refresh token을 함께 받습니다.
                    - `expiresIn`은 access token의 남은 유효 시간(초)입니다. 현재 값은 1800(30분)입니다.
                    - refresh token에는 만료가 없습니다. 앱이 보관하는 장기 비밀값은 이 하나뿐입니다.
                    - 응답에 기기 식별자는 포함되지 않습니다. 기기는 서버가 토큰으로부터 찾습니다.

                    ### requestId

                    - `requestId`는 재시도 시 중복 등록을 막는 멱등 키입니다.
                    - 새 등록마다 새 UUID를 만들고, 같은 등록의 재시도에는 같은 값을 재사용합니다.
                    - **등록 후 5분 동안은 이 값만으로 그 기기의 토큰을 다시 받을 수 있습니다.**
                      그동안은 자격증명처럼 다뤄야 하므로 로그에 남기거나 외부에 넘기지 않습니다.
                    - 등록에 성공하면 앱은 `requestId`를 즉시 폐기합니다.

                    ### 등록 재시도

                    - 응답을 받지 못했다면 같은 `requestId`로 다시 요청합니다.
                    - 최초 등록은 201, 같은 `requestId`의 재요청은 200을 반환합니다. 둘 다 성공입니다.
                    - 재시도는 최초 등록 후 5분 이내에만 토큰을 다시 받을 수 있습니다.
                    - 5분이 지난 뒤 같은 `requestId`로 요청하면 409를 반환하고 토큰을 주지 않습니다.
                      이때 앱은 새 `requestId`로 처음부터 등록합니다.
                    - 재시도로 받은 refresh token은 매번 새 값입니다. 앱은 마지막으로 받은 값을 보관합니다.
                      이전에 받은 값도 계속 사용할 수 있습니다.

                    ### attestation

                    - `platform`은 필수이며 `ANDROID` 또는 `IOS`입니다. 이 값만 서버에 저장합니다.
                    - `token`은 무결성 증명 토큰이고 `challenge`는 그 토큰을 만들 때 쓴 값입니다. 둘 다 저장하지 않습니다.
                    - **`token`을 생략하거나 `null`로 두면 무결성 증명을 검증하지 않고 등록합니다.**
                      증명을 아직 붙이지 않은 앱이 그대로 동작하기 위한 전환기 동작이며, 앱 전환이 끝나면 필수가 됩니다.
                      이때 `challenge`도 보내지 않습니다.
                    - **`token`을 보내면 `challenge`도 필수입니다.** 없거나 이미 썼거나 만료된 값이면 400을 반환합니다.
                    - **`token`을 보내면 서버가 실제로 검증합니다.** 검증에 실패하면 403이고 기기는 등록되지 않습니다.
                    - `ANDROID`는 Play Integrity로, `IOS`는 App Attest로 검증합니다. 검증 수단이 다르므로
                      `challenge`를 소모하는 시점도 다릅니다.
                    - `ANDROID`는 요청에 담아 보낸 값을 복호화 전 사전 조회에만 쓰고, 복호화한 토큰 안의 challenge를
                      기준으로 소모합니다. 두 값이 다르면 403이고 challenge는 남습니다.
                    - `IOS`는 인증서 체인을 검증하기 전에 요청에 담아 보낸 `challenge`를 소모합니다.
                      애플은 서버가 외부에 조회하지 않으므로, 같은 값으로 검증을 반복하는 것을 여기서 막습니다.
                      **그래서 검증에 실패해도 challenge는 사라집니다.** 다시 시도하려면 새로 발급받습니다.
                    - 검증은 최초 등록에서만 합니다. 재시도 창(5분) 안의 같은 `requestId` 재요청은 검증하지 않습니다.
                      challenge가 1회용이므로 재시도마다 새 증명을 요구하면 재시도 자체가 불가능해집니다.
                    - 검증 수단을 일시적으로 쓸 수 없으면 503을 반환합니다. 이때만 `Retry-After` 헤더가 붙습니다.
                      `ANDROID` 경로에만 해당합니다. `IOS`는 외부에 조회하지 않으므로 503을 내지 않습니다.
                    - `keyId`는 `IOS`가 `token`을 보낼 때 필수입니다. attestation 객체의 공개 키 해시와 대조하며
                      저장하지는 않습니다. `ANDROID`는 쓰지 않습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "기기 등록 성공",
                    content = @Content(schema = @Schema(implementation = DeviceTokenResponse.class))
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "재시도 창(5분) 안의 같은 requestId 재요청이라 토큰을 다시 발급",
                    content = @Content(schema = @Schema(implementation = DeviceTokenResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "requestId나 attestation.platform이 없거나 형식이 올바르지 않음. "
                            + "또는 attestation.token을 보냈는데 attestation.challenge가 없거나 "
                            + "이미 썼거나 만료됨. IOS는 검증에 실패해 소모된 challenge를 다시 보낸 경우도 포함한다",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "요청 값 오류", value = """
                                            {"code":"COMMON-001","message":"요청 값이 올바르지 않습니다."}
                                            """),
                                    @ExampleObject(name = "challenge 재사용 또는 만료", value = """
                                            {"code":"DEVICE-005","message":"무결성 증명 요청 값을 사용할 수 없습니다."}
                                            """)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "무결성 증명 검증에 실패함. ANDROID는 보낸 challenge와 토큰 안의 challenge가 다른 경우를, "
                            + "IOS는 attestation 객체 형식 오류, 인증서 체인 검증 실패, App ID 불일치, keyId 누락이나 불일치를 포함한다. "
                            + "재시도로 복구되지 않으며 정식 빌드에서 다시 시도해야 함",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"DEVICE-006","message":"무결성 증명을 확인할 수 없습니다."}
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "재시도 창이 지난 뒤 같은 requestId로 등록을 요청함. 새 requestId로 다시 등록해야 함",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"DEVICE-002","message":"기기 등록 재시도 시간이 지났습니다. 새 요청으로 등록해 주세요."}
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "기기를 저장하지 못했거나 처리하지 못한 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "무결성 증명 검증 수단을 일시적으로 쓸 수 없음. ANDROID 경로에만 해당하며 IOS는 이 응답을 내지 않는다. "
                            + "Retry-After 헤더의 초만큼 기다린 뒤 "
                            + "같은 requestId로 다시 요청한다",
                    headers = @Header(
                            name = HttpHeaders.RETRY_AFTER,
                            description = "다시 요청하기까지 기다릴 시간(초)",
                            schema = @Schema(type = "integer", example = "60")
                    ),
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"DEVICE-007","message":"무결성 증명을 지금 확인할 수 없습니다. 잠시 후 다시 시도해 주세요."}
                                    """)
                    )
            )
    })
    ResponseEntity<DeviceTokenResponse> save(DeviceCreateRequest request);

    @Operation(
            summary = "무결성 증명 challenge 발급",
            description = """
                    ### 무엇을 받나요

                    - 무결성 증명 요청에 넣을 1회용 값 하나를 받습니다.
                    - 요청 본문과 파라미터가 없습니다. 아무것도 보내지 않습니다.
                    - `expiresIn`은 challenge의 유효 시간(초)입니다. 현재 값은 300(5분)이며 서버가 정합니다.

                    ### 어떻게 쓰나요

                    - 증명 토큰을 기기 등록(`POST /api/v2/devices`) 요청의 `attestation.token`으로 보냅니다.
                    - **받은 `challenge`도 같은 요청의 `attestation.challenge`로 함께 보냅니다.** 플랫폼과 무관하게 필수입니다.
                    - **`ANDROID`** — 받은 `challenge` 문자열을 Play Integrity 요청의 nonce로 그대로 씁니다.
                      서버는 복호화한 토큰 안의 challenge를 권위 있는 값으로 삼습니다. 두 값이 다르면 403입니다.
                    - **`IOS`** — 받은 `challenge` 문자열의 **US-ASCII 바이트를 SHA-256 한 32바이트**를
                      `attestKey(_:clientDataHash:)`의 `clientDataHash`로 넘깁니다.
                      **challenge 문자열 자체를 넘기지 않습니다.** 애플 예제 코드가 원문을 그대로 넘기는 것과 다릅니다.
                      잘못 만들면 서버가 nonce 불일치로 403을 반환하며, 응답만으로는 원인을 알 수 없습니다.

                    ### 1회용입니다

                    - 한 번 쓰이면 다시 쓸 수 없습니다. 두 번째 사용은 400입니다.
                    - **소모 시점이 플랫폼마다 다릅니다.** `ANDROID`는 복호화한 토큰 안의 challenge가 맞을 때 소모합니다.
                      `IOS`는 검증을 시작하기 전에 소모하므로 **검증에 실패해도 그 challenge는 사라집니다.**
                    - 5분이 지나면 쓸 수 없습니다. 만료된 값도 400입니다.
                    - 증명을 다시 시도할 때는 challenge를 새로 발급받습니다. 보관하거나 재사용하지 않습니다.

                    ### 인증

                    - 기기 등록 전에 부르는 엔드포인트이므로 인증이 필요하지 않습니다.
                    - challenge는 자격증명이 아닙니다. 이 값만으로는 어떤 권한도 얻지 못합니다.

                    ### 현재 버전의 제약

                    - **기기 등록은 아직 무결성 증명을 요구하지 않습니다.** `attestation.token`을 생략하면
                      증명 없이 등록되며 이 엔드포인트를 부르지 않아도 됩니다.
                    - 다만 `attestation.token`을 보내면 서버가 실제로 검증하고 `attestation.challenge`도 요구합니다.
                      증명을 붙일 준비가 되었다면 이 엔드포인트로 challenge를 받아 두 값을 함께 보냅니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "challenge 발급 성공",
                    content = @Content(schema = @Schema(implementation = DeviceChallengeResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "challenge를 발급하지 못한 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    DeviceChallengeResponse issueChallenge();

    @Operation(
            summary = "access token 재발급",
            description = """
                    ### 요청

                    - 기기 등록 때 받은 `refreshToken`만 보냅니다. 기기 식별자는 보내지 않습니다.
                    - 서버가 refresh token으로부터 기기를 찾습니다.

                    ### 응답

                    - access token만 반환합니다. refresh token은 새로 발급되지 않습니다.
                    - 앱은 등록 때 받은 refresh token을 계속 보관합니다.
                    - `expiresIn`은 새 access token의 유효 시간(초)입니다.

                    ### 실패

                    - 사용할 수 없거나 폐기된 refresh token이면 401을 반환합니다.
                    - 401을 받으면 재시도로 복구되지 않습니다. 새 `requestId`로 기기를 다시 등록해야 합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "access token 재발급 성공",
                    content = @Content(schema = @Schema(implementation = AccessTokenResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "refreshToken이 없거나 비어 있음",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"COMMON-001","message":"요청 값이 올바르지 않습니다."}
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "사용할 수 없거나 폐기된 refresh token",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"DEVICE-003","message":"인증 정보를 사용할 수 없습니다."}
                                    """)
                    )
            )
    })
    AccessTokenResponse reissueAccessToken(AccessTokenReissueRequest request);
}
