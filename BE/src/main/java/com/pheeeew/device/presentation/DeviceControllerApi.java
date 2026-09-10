package com.pheeeew.device.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.device.presentation.dto.AccessTokenReissueRequest;
import com.pheeeew.device.presentation.dto.AccessTokenResponse;
import com.pheeeew.device.presentation.dto.DeviceCreateRequest;
import com.pheeeew.device.presentation.dto.DeviceTokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

                    - `requestId`는 자격증명이 아니라 재시도 시 중복 등록을 막는 멱등 키입니다.
                    - 새 등록마다 새 UUID를 만들고, 같은 등록의 재시도에는 같은 값을 재사용합니다.
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
                    - `token`과 `keyId`는 무결성 증명 값입니다. 현재 버전에서는 검증하지 않으며 저장하지도 않습니다.
                      아직 보낼 값이 없으면 `null`로 두거나 생략합니다.
                    - 검증이 추가되면 이 두 필드가 필수가 됩니다. 요청 구조는 그대로입니다.
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
                    description = "requestId나 attestation.platform이 없거나 형식이 올바르지 않음",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"COMMON-001","message":"요청 값이 올바르지 않습니다."}
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
            )
    })
    ResponseEntity<DeviceTokenResponse> save(DeviceCreateRequest request);

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
