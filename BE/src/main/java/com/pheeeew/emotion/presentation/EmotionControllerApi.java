package com.pheeeew.emotion.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.emotion.domain.EmojiType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
            @ApiResponse(responseCode = "404", description = "감정이 없거나 삭제됨",
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
            @ApiResponse(responseCode = "404", description = "감정이 없거나 삭제됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> cancel(
            @Parameter(description = "감정 ID", example = "42", schema = @Schema(minimum = "1"))
            @Min(value = 1, message = "감정 ID는 1 이상이어야 합니다.") Long emotionId,
            @Parameter(description = "이모지 코드", example = "HEART") EmojiType emojiType,
            @Parameter(hidden = true) UUID devicePublicId
    );
}
