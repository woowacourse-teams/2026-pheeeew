package com.pheeeew.appversion.presentation;

import com.pheeeew.appversion.presentation.dto.AppVersionResponse;
import com.pheeeew.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;

@Tag(name = "앱 버전", description = "앱 시작 시 업데이트 판단에 사용하는 플랫폼별 버전 정책 조회 API")
public interface AppVersionControllerApi {

    @Operation(
            summary = "플랫폼별 활성 앱 버전 조회",
            description = """
                    ### 호출 시점과 입력

                    - 앱 시작 시 기기 등록과 인증 전에 호출합니다. `Authorization` 헤더를 보내지 않습니다.
                    - `platform`은 필수이며 `android`, `ios`를 대소문자 구분 없이 받습니다.
                    - 누락, 빈 문자열, 앞뒤 공백이 포함된 값과 지원하지 않는 플랫폼은 400을 반환합니다.
                    - 해당 플랫폼의 정책이 없거나 비활성이면 404를 반환합니다.

                    ### 앱의 버전 비교

                    - 서버는 정책을 제공하고 앱이 설치 버전을 비교합니다.
                    - 버전은 정식 출시용 `major.minor.patch` 형식입니다. 각 항목을 숫자로 비교합니다.
                    - 예를 들어 `1.10.0`은 `1.9.0`보다 높은 버전입니다.
                    - 설치 버전이 `minSupportedVersion` 미만이면 업데이트 안내 후 진입을 차단합니다.
                    - 최소 지원 버전 이상이며 `latestVersion` 미만이면 건너뛸 수 있는 업데이트를 안내합니다.
                    - `latestVersion` 이상이면 정상 진입합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "활성 앱 버전 정책 조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AppVersionResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "APP_VERSION-001: 플랫폼이 누락되었거나 올바르지 않음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "APP_VERSION-002: 해당 플랫폼의 활성 정책이 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    AppVersionResponse findByPlatformAndActiveTrue(
            @Parameter(description = "조회할 앱 플랫폼. android 또는 ios이며 대소문자를 구분하지 않습니다.",
                    required = true, example = "android")
            String platform
    );
}
