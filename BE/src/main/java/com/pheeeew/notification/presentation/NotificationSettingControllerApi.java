package com.pheeeew.notification.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.notification.presentation.dto.NotificationSettingsResponse;
import com.pheeeew.notification.presentation.dto.NotificationSettingsUpdateRequest;
import com.pheeeew.notification.presentation.dto.PushTokenUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "알림 설정", description = "푸시 등록 값과 기기별 알림 수신 설정 API")
public interface NotificationSettingControllerApi {

    @Operation(summary = "푸시 등록 값 저장", security = @SecurityRequirement(name = "bearerAuth"),
            description = """
                    - 인증된 본인 기기의 푸시 등록 값을 저장합니다. 기기 식별자는 보내지 않습니다.
                    - token 에는 Firebase SDK 가 준 값을 그대로 보냅니다. FID 와 기존 등록 토큰을 모두 받으며 서버는 형식을 해석하지 않습니다.
                    - 이 API 가 지원하는 token 상한은 UTF-8 1024바이트입니다. 넘거나 널 문자가 있으면 400 입니다.
                    - 기기마다 값은 하나입니다. 다른 값을 보내면 이전 값을 바꿉니다.
                    - 같은 값을 여러 번 보내도 결과가 같습니다. 앱이 활성화될 때마다(콜드 스타트, 포그라운드 복귀) OS 알림 권한이 허용이면 현재 값을 보내고, SDK 가 새 값을 알려 줄 때도 보냅니다.
                    - 다른 기기에 같은 값이 있으면 그 기기에서 떼어 이 기기로 옮깁니다.
                    - 알림 수신 설정은 바꾸지 않습니다.
                    - platform 은 보내지 않습니다. 서버는 기기를 등록할 때 저장한 플랫폼을 씁니다.
                    - 성공하면 본문 없는 204 를 반환합니다. 저장한 값은 어떤 API 로도 돌려주지 않습니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "저장 성공. 같은 값을 다시 보내도 204", content = @Content),
            @ApiResponse(responseCode = "400", description = "COMMON-001: token 누락, 공백뿐인 값, 1024자 또는 UTF-8 1024바이트 초과, 널 문자 포함 또는 잘못된 본문",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "AUTH-001: 인증 토큰 누락, 무효. DEVICE-004: 기기가 존재하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> updatePushToken(@Parameter(hidden = true) UUID devicePublicId, PushTokenUpdateRequest request);

    @Operation(summary = "푸시 등록 값 해제", security = @SecurityRequirement(name = "bearerAuth"),
            description = """
                    - 이 기기를 푸시 발송 대상에서 뺍니다. OS 알림 권한이 거부되면 호출합니다.
                    - 저장된 값이 없어도 204 를 반환합니다.
                    - 알림 수신 설정은 지우지 않습니다. 다시 등록하면 이전 설정이 그대로 적용됩니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "해제 성공. 저장된 값이 없어도 204", content = @Content),
            @ApiResponse(responseCode = "401", description = "AUTH-001: 인증 토큰 누락, 무효. DEVICE-004: 기기가 존재하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> deletePushToken(@Parameter(hidden = true) UUID devicePublicId);

    @Operation(summary = "알림 수신 설정 조회", security = @SecurityRequirement(name = "bearerAuth"),
            description = """
                    - 이 기기의 컬렉션 발급 알림(collectionIssuedEnabled)과 순위 하락 알림(rankDroppedEnabled) 수신 여부를 조회합니다.
                    - 한 번도 바꾸지 않은 기기는 두 값 모두 true 입니다.
                    - 기기별 값이며 다른 기기에 영향을 주지 않습니다.
                    - OS 알림 권한과 별개입니다. true 여도 OS 권한이 꺼져 있으면 기기에 표시되지 않습니다.
                    - 푸시 등록 값이 없어도 조회할 수 있습니다.
                    - 현재 값을 확인해야 하므로 응답에 Cache-Control: no-store 를 설정합니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 바꾼 적이 없으면 두 값 모두 true",
                    content = @Content(schema = @Schema(implementation = NotificationSettingsResponse.class))),
            @ApiResponse(responseCode = "401", description = "AUTH-001: 인증 토큰 누락, 무효. DEVICE-004: 기기가 존재하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<NotificationSettingsResponse> findSettings(@Parameter(hidden = true) UUID devicePublicId);

    @Operation(summary = "알림 수신 설정 변경", security = @SecurityRequirement(name = "bearerAuth"),
            description = """
                    - 두 값을 모두 보내는 전체 교체입니다. 하나라도 빠지거나 null 이면 400 입니다.
                    - 이 기기에만 적용합니다.
                    - 서버는 알림을 보낼 때 그 시점의 설정을 확인합니다.
                    - 같은 값을 여러 번 보내도 결과가 같습니다. 푸시 등록 값을 바꾸거나 다시 등록하거나 해제해도 이 설정은 바뀌지 않습니다.
                    - 푸시 등록 값이 없어도 저장합니다.
                    - 성공하면 본문 없는 204 를 반환합니다. 현재 값은 GET /api/v3/devices/me/notification-settings 로 조회합니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "변경 성공", content = @Content),
            @ApiResponse(responseCode = "400", description = "COMMON-001: 필드 누락, null 또는 잘못된 본문",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "AUTH-001: 인증 토큰 누락, 무효. DEVICE-004: 기기가 존재하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> updateSettings(@Parameter(hidden = true) UUID devicePublicId, NotificationSettingsUpdateRequest request);
}
