package com.pheeeew.emotion.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.emotion.presentation.dto.EmotionRegionMapResponse;
import com.pheeeew.emotion.presentation.dto.EmotionV4DetailResponse;
import com.pheeeew.emotion.presentation.dto.EmotionV4RegionMapRequest;
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
}
