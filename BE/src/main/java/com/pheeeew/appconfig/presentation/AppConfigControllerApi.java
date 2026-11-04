package com.pheeeew.appconfig.presentation;

import com.pheeeew.appconfig.presentation.dto.AppConfigResponse;
import com.pheeeew.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "앱 설정", description = "일일 프레스 순위 표시와 공지의 플랫폼별 원격 설정 조회 API")
public interface AppConfigControllerApi {

    @Operation(
            summary = "플랫폼별 앱 설정 조회",
            description = """
                    ### 호출과 오류

                    - 로그인·기기 등록 전에 호출할 수 있습니다. `Authorization` 헤더를 보내지 않습니다.
                    - `platform`은 필수이며 `android`, `ios`를 대소문자 구분 없이 받습니다.
                    - 누락·빈 문자열·앞뒤 공백·미지원 값은 400, 해당 플랫폼의 설정이 없으면 404입니다.
                    - 응답은 `Cache-Control: no-store`이며 매번 최신 설정을 조회합니다.

                    ### 적용 범위

                    - 모든 응답 필드는 필수입니다. 초기값은 순위 활성화 `true`, 공지 `""`, revision `1`입니다.
                    - 공지는 일반 텍스트 최대 200 Unicode 코드 포인트이며 공백과 줄바꿈을 보존합니다.
                    - 실제 설정 변경과 복구마다 플랫폼별 revision이 증가하고 UTC 변경 시각이 갱신됩니다.
                    - 일일 순위 화면 제어는 앱이 수행합니다. 서버 입력·집계·조회·알림은 차단하지 않습니다.
                    - 기존 앱 버전 정책 API는 별도로 유지합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "앱 설정 조회 성공",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AppConfigResponse.class))),
            @ApiResponse(responseCode = "400", description = "APP_CONFIG-001: 플랫폼이 누락되었거나 올바르지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "APP_CONFIG-002: 해당 플랫폼의 설정이 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AppConfigResponse> findByPlatform(
            @Parameter(description = "android 또는 ios. 대소문자는 구분하지 않으며 앞뒤 공백은 허용하지 않습니다.",
                    required = true, example = "android") String platform,
            @Parameter(hidden = true) HttpServletResponse response
    );
}
