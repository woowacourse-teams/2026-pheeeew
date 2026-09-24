package com.pheeeew.emotion.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.common.presentation.dto.CursorResponse;
import com.pheeeew.emotion.presentation.dto.EmotionListRequest;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.presentation.dto.EmotionDetailResponse;
import com.pheeeew.emotion.presentation.dto.EmotionCreateRequest;
import com.pheeeew.emotion.presentation.dto.EmotionCreateResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "감정", description = "감정과 이모지 API")
public interface EmotionControllerApi {

    @Operation(summary = "감정 지도·목록 조회", description = """
            첫 페이지에는 minLongitude, minLatitude, maxLongitude, maxLatitude를 전달합니다.
            다음 페이지에는 반환된 cursor만 전달합니다. 날짜변경선을 넘는 영역은 minLongitude > maxLongitude로 표현합니다.
            기간 제한 없이 (createdAt DESC, id DESC) 순으로 최대 20개씩 조회합니다.
            지도는 모든 페이지를 모아 오래된 감정부터 그려 최신 감정이 위에 표시되도록 합니다.
            최초 조회 이후 작성된 감정은 제외하고, 삭제·차단은 매 페이지에 반영합니다.
            각 항목은 상세와 같은 GeoJSON Feature이며 여섯 이모지 집계와 본인 선택 여부를 포함합니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "감정 목록과 다음 커서"),
            @ApiResponse(responseCode = "400", description = "영역 또는 커서가 올바르지 않음"),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음")
    })
    ResponseEntity<CursorResponse<EmotionDetailResponse>> findAll(@Valid EmotionListRequest request,
            @Parameter(hidden = true) UUID devicePublicId);


    @Operation(summary = "감정 등록", description = """
            선택 위치에 감정을 등록합니다. contentType은 NONE, MEMO, AUDIO 중 하나입니다.
            최초 등록과 같은 기기의 requestId 재시도 모두 최초 감정 ID를 200으로 반환합니다.
            다른 기기가 사용한 requestId는 409입니다. 녹음은 업로드 완료된 audioUploadId로 연결합니다.
            그룹 스탬프 선택은 아직 지원하지 않습니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장된 감정 ID"),
            @ApiResponse(responseCode = "400", description = "등록 필드 또는 내용 조합이 올바르지 않음"),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음"),
            @ApiResponse(responseCode = "404", description = "녹음 업로드가 없거나 해당 기기의 업로드가 아님"),
            @ApiResponse(responseCode = "409", description = "요청 식별자 충돌 또는 녹음 미완료·이미 사용됨"),
            @ApiResponse(responseCode = "503", description = "녹음 확인 기능을 사용할 수 없음")
    })
    ResponseEntity<EmotionCreateResponse> save(@Valid EmotionCreateRequest request,
            @Parameter(hidden = true) UUID devicePublicId);


    @Operation(summary = "감정 상세 조회", description = """
            감정 정보와 여섯 이모지 코드별 전체 선택 수 및 인증된 기기의 선택 여부를 함께 반환합니다.
            조회 기간 제한은 없으며 삭제되거나 인증된 기기가 차단한 감정·작성자의 감정은 반환하지 않습니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "감정 상세 조회 성공",
                    headers = @Header(name = "Cache-Control", description = "기기별 응답을 개인 캐시에 저장할 수 있지만 재사용 전 서버 확인이 필요합니다.",
                            schema = @Schema(type = "string", example = "no-cache, private")),
                    content = @Content(mediaType = "application/geo+json",
                            schema = @Schema(implementation = EmotionDetailResponse.class))),
            @ApiResponse(responseCode = "400", description = "감정 ID가 올바르지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "감정이 없거나 삭제·차단됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<EmotionDetailResponse> findById(
            @Parameter(description = "감정 ID", example = "42", schema = @Schema(minimum = "1"))
            @Min(value = 1, message = "감정 ID는 1 이상이어야 합니다.") Long emotionId,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(summary = "감정 이모지 선택", description = """
            인증된 기기가 선택한 이모지를 저장합니다. 같은 요청을 다시 보내도 한 번만 저장합니다.
            요청 본문 없이 경로의 영문 이모지 코드를 사용합니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "선택 완료 또는 이미 선택됨"),
            @ApiResponse(responseCode = "400", description = "감정 ID나 이모지 코드가 올바르지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "감정이 없거나 삭제·차단됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> select(
            @Parameter(description = "감정 ID", example = "42", schema = @Schema(minimum = "1"))
            @Min(value = 1, message = "감정 ID는 1 이상이어야 합니다.") Long emotionId,
            @Parameter(description = "이모지 코드", example = "HEART") EmojiType emojiType,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(summary = "감정 이모지 선택 취소", description = """
            인증된 기기의 선택을 취소합니다. 선택하지 않은 상태에서 다시 요청해도 취소 상태를 유지합니다.
            요청 본문 없이 경로의 영문 이모지 코드를 사용합니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "취소 완료 또는 이미 선택되지 않음"),
            @ApiResponse(responseCode = "400", description = "감정 ID나 이모지 코드가 올바르지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "감정이 없거나 삭제·차단됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> cancel(
            @Parameter(description = "감정 ID", example = "42", schema = @Schema(minimum = "1"))
            @Min(value = 1, message = "감정 ID는 1 이상이어야 합니다.") Long emotionId,
            @Parameter(description = "이모지 코드", example = "HEART") EmojiType emojiType,
            @Parameter(hidden = true) UUID devicePublicId
    );
}
