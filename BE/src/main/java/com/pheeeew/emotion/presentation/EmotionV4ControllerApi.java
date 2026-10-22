package com.pheeeew.emotion.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.common.presentation.dto.CursorResponse;
import com.pheeeew.emotion.presentation.dto.EmotionCreateResponse;
import com.pheeeew.emotion.presentation.dto.EmotionRegionMapResponse;
import com.pheeeew.emotion.presentation.dto.EmotionV4CreateRequest;
import com.pheeeew.emotion.presentation.dto.EmotionV4DetailResponse;
import com.pheeeew.emotion.presentation.dto.EmotionV4MapRequest;
import com.pheeeew.emotion.presentation.dto.EmotionV4MapResponse;
import com.pheeeew.emotion.presentation.dto.EmotionV4RegionMapRequest;
import com.pheeeew.emotion.presentation.dto.EmotionV4UpdateRequest;
import com.pheeeew.emotion.presentation.dto.EmotionsV4WithoutBoundsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

@Tag(name = "감정 v4", description = "그룹 입출력이 없는 감정 API")
public interface EmotionV4ControllerApi {

    @Operation(summary = "감정 상세 조회", description = """
            감정 상세를 GeoJSON Feature로 반환합니다. 그룹 스탬프가 연결된 감정도 조회할 수 있으나
            응답에는 groupId와 groupStamp가 없습니다. 메모·녹음·닉네임·공감·isMine은 기존 상세와 같습니다.
            인증된 기기를 기준으로 가시성과 차단을 적용하며 조회할 수 없는 감정은 404입니다.
            groupId는 빈 값·null 문자열을 포함해 입력 자체를 400으로 거부하고 scope=GROUP도 400입니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "감정 상세"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 유효하지 않은 기기",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "조회할 수 없는 감정",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "녹음 재생 URL 발급 불가",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<EmotionV4DetailResponse> findById(
            @Parameter(description = "감정 ID", example = "42", schema = @Schema(minimum = "1"))
            @Min(value = 1, message = "감정 ID는 1 이상이어야 합니다.") Long emotionId,
            @Parameter(hidden = true) UUID devicePublicId,
            @Parameter(hidden = true) MultiValueMap<String, String> parameters
    );

    @Operation(summary = "그룹 없는 지역별 감정 요약 조회", description = """
            SIDO·SIGUNGU는 bbox와 무관한 전국 요약, EMD는 화면 교차 지역 요약을 반환합니다.
            모든 계층에서 bbox는 필수이며 날짜변경선을 넘는 경도 범위를 허용합니다.
            지역 전체 누적 개수와 대표 감정 및 고정 표시점을 반환하고 0건 지역은 생략합니다.
            대표 감정 동률 우선순위는 ANGRY, DISCOURAGED, EXHAUSTED, FRUSTRATED, IRRITATED입니다.

            scope는 ALL·MY만 허용하며 생략하면 ALL입니다. MY는 인증된 기기의 기록입니다.
            시작일·종료일은 KST 기준으로 함께 전달하며 둘 다 생략하면 전체 기간입니다.
            contentType은 ALL·MEMO·AUDIO이며 NONE은 제외합니다. 개인별 차단은 지역 요약에 적용하지 않습니다.
            groupId는 빈 값·null 문자열을 포함해 입력 자체를 400으로 거부하고 scope=GROUP도 400입니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "지역 요약. 결과가 없으면 빈 배열"),
            @ApiResponse(responseCode = "400", description = "잘못된 좌표·날짜·콘텐츠·범위 또는 금지된 그룹 입력",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 유효하지 않은 기기",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "지역 요약 준비 미완료",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<List<EmotionRegionMapResponse>> findRegionMap(
            @Valid @ParameterObject EmotionV4RegionMapRequest request,
            @Parameter(hidden = true) UUID devicePublicId,
            @Parameter(hidden = true) MultiValueMap<String, String> parameters
    );

    @Operation(summary = "그룹 없는 감정 목록 조회", description = """
            좌표 없이 감정을 최신 작성 시각·ID 내림차순으로 최대 50개 조회합니다.
            그룹이 연결된 감정도 포함하지만 groupId·groupStamp는 응답하지 않습니다.
            내용 없는 감정과 삭제·차단된 감정은 제외하며 인증된 기기를 기준으로 MY와 공감 선택 상태를 계산합니다.

            scope는 ALL·MY이며 첫 페이지에서 생략하면 ALL입니다. contentType은 ALL·MEMO·AUDIO입니다.
            KST 기준 시작일·종료일은 함께 전달하며 둘 다 생략하면 전체 기간입니다.
            groupId는 빈 값도 400으로 거부하며 scope=GROUP도 400입니다.

            다음 페이지에는 v4 목록이 발급한 nextCursor를 전달합니다. 기존 API·v4 핀 커서는 400입니다.
            필터는 생략하거나 이전과 동일하게 전달합니다. 다른 조건은 400입니다.
            커서의 조회 기준 시각 이후 새 기록은 제외하지만 삭제·차단은 각 요청 시점에 반영합니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "감정 목록과 다음 페이지 정보"),
            @ApiResponse(responseCode = "400", description = "잘못된 필터·금지된 그룹 입력 또는 잘못된 커서",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 유효하지 않은 기기",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "녹음 재생 URL 발급 불가",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CursorResponse<EmotionV4DetailResponse>> findListWithoutBoundsV4(
            @Valid @ParameterObject EmotionsV4WithoutBoundsRequest request,
            @Parameter(hidden = true) UUID devicePublicId,
            @Parameter(hidden = true) MultiValueMap<String, String> parameters
    );

    @Operation(summary = "그룹 없는 감정 지도 핀 조회", description = """
            화면 안의 내용 있는 감정을 최신 작성 시각·ID 내림차순으로 반환합니다.
            limit을 생략하면 최대 200개이며 개발 환경은 1~1000개, 그 외 환경은 1~200개를 허용합니다.
            limit의 빈 값·잘못된 숫자·0·음수·환경별 상한 초과는 400이며 개수를 자동으로 줄이지 않습니다.
            그룹 감정도 포함하지만 groupId·groupStamp는 반환하지 않습니다. 삭제·차단된 감정은 제외합니다.
            첫 페이지에는 bbox 네 값을 모두 전달하며 날짜변경선을 넘는 경도 범위를 허용합니다.

            scope는 ALL·MY이고 첫 페이지에서 생략하면 ALL입니다. contentType은 ALL·MEMO·AUDIO입니다.
            KST 시작일·종료일은 함께 전달하고 둘 다 생략하면 전체 기간입니다.
            groupId는 빈 값도 400으로 거부하며 scope=GROUP도 400입니다.

            다음 페이지에는 v4 핀이 발급한 nextCursor를 cursor로 전달하고 bbox는 생략합니다.
            limit은 커서에 저장하지 않으며 다음 요청에서 변경할 수 있습니다. 생략하면 다시 200개를 적용합니다.
            기존 API·v4 목록 커서는 400입니다. 필터는 생략하거나 커서와 같게 전달합니다.
            커서의 기준 시각 이후 등록은 제외하지만 삭제·차단은 조회 시점에 다시 반영합니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "GeoJSON 핀 목록과 다음 페이지 정보"),
            @ApiResponse(responseCode = "400", description = "잘못된 좌표·필터·커서·limit 또는 금지된 그룹 입력",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 유효하지 않은 기기",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CursorResponse<EmotionV4MapResponse>> findContentMapWithinBoundsV4(
            @Valid @ParameterObject EmotionV4MapRequest request,
            @Parameter(hidden = true) UUID devicePublicId,
            @Parameter(hidden = true) MultiValueMap<String, String> parameters
    );

    @Operation(summary = "그룹 없는 감정 등록", description = """
            MEMO·AUDIO 감정을 그룹 연결 없이 등록합니다. groupId는 null을 포함해 입력 자체를 400으로 거부합니다.
            requestId는 재시도마다 동일하게 전달합니다. 같은 기기의 재시도에는 최초 감정 ID를 반환하고
            기존 그룹 감정의 재시도여도 저장된 감정과 그룹 연결을 변경하지 않습니다.

            anonymous는 JSON boolean만 허용하며 생략·null이면 익명입니다. 기명 등록에는 기기 닉네임이 필요합니다.
            내용·좌표·각도·녹음 업로드 검증과 기기별 1초 등록 제한은 기존 등록과 같습니다.
            성공하면 200과 감정 ID를 반환하고 Location은 v4 상세 경로를 가리킵니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 또는 동일 요청의 최초 감정 ID"),
            @ApiResponse(responseCode = "400", description = "잘못된 내용·위치·녹음·그룹 입력",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 유효하지 않은 기기",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "녹음 업로드 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "다른 기기의 requestId·사용할 수 없는 녹음 또는 기명 등록에 필요한 닉네임 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "등록 빈도 제한. Retry-After 확인",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "녹음 저장소 확인 불가",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<EmotionCreateResponse> saveV4(
            @Valid EmotionV4CreateRequest request,
            @Parameter(hidden = true) UUID devicePublicId,
            @Parameter(hidden = true) MultiValueMap<String, String> parameters
    );

    @Operation(summary = "그룹 연결을 유지하는 감정 수정", description = """
            인증된 기기가 작성한 감정의 상태·메모·녹음을 수정합니다. 기존 그룹 연결은 그대로 유지합니다.
            groupId는 null을 포함해 입력 자체를 400으로 거부합니다. 기존 그룹의 삭제·탈퇴 여부를 다시 검증하지 않습니다.
            위치·회전·작성 시각·등록 당시 익명 설정은 변경하지 않습니다.

            contentType은 MEMO·AUDIO만 허용합니다. AUDIO에서 audioUploadId를 생략하거나 null로 두면
            기존 녹음을 유지하며 기존 녹음이 없으면 400입니다. 새 업로드를 전달하면 교체합니다.
            소유자가 아니거나 삭제된 감정은 404이며 성공하면 본문 없이 204입니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "수정 완료"),
            @ApiResponse(responseCode = "400", description = "잘못된 내용·금지된 그룹 입력 또는 유지할 녹음 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 유효하지 않은 기기",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "수정할 수 없는 감정 또는 없는 업로드",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "사용할 수 없는 녹음 업로드",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "녹음 저장소 확인 불가",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> updateWithoutGroupChange(
            @Min(value = 1, message = "감정 ID는 1 이상이어야 합니다.") Long emotionId,
            @Valid EmotionV4UpdateRequest request,
            @Parameter(hidden = true) UUID devicePublicId,
            @Parameter(hidden = true) MultiValueMap<String, String> parameters
    );
}
