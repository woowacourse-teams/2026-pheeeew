package com.pheeeew.emotion.presentation;

import com.pheeeew.emotion.presentation.dto.AudioUploadRequest;
import com.pheeeew.emotion.presentation.dto.AudioUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "녹음 업로드", description = "감정에 연결할 녹음 업로드 API")
public interface AudioUploadControllerApi {

    @Operation(summary = "녹음 업로드 URL 발급", description = """
            인증된 기기 전용 업로드 식별자와 5분 동안 유효한 S3 PUT URL을 발급합니다.
            contentType은 audio/mp4, contentLength는 파일 본문의 바이트 수(1~5,242,880)입니다.
            앱은 녹음 길이를 최대 1분으로 제한해야 합니다. 이 API는 실제 녹음 길이를 검사하지 않습니다.

            반환된 uploadUrl에 headers를 그대로 포함하여 녹음 파일 본문을 PUT으로 전송합니다.
            multipart 형식이나 이 서버의 Bearer 토큰을 S3에 보내지 않습니다.
            동일 객체의 덮어쓰기는 허용하지 않습니다. URL이 만료되면 새로 발급받습니다.
            발급 요청을 반복하면 각각 별도의 uploadId와 URL을 발급합니다.

            S3 업로드 성공 후 감정 등록·수정 요청의 contentType을 AUDIO로 지정하고,
            audioUploadId에 uploadId를 전달합니다. 감정 연결 시 소유자와 실제 객체의 크기·MIME을 확인합니다.
            미연결 업로드는 발급 후 24시간 안에 감정에 연결해야 합니다. 이 기한은 파일 자동 삭제를 뜻하지 않습니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "업로드 식별자, PUT URL, 만료 시각과 필수 헤더"),
            @ApiResponse(responseCode = "400", description = "형식·크기가 올바르지 않거나 필수값이 없음"),
            @ApiResponse(responseCode = "401", description = "인증할 수 없거나 등록되지 않은 기기"),
            @ApiResponse(responseCode = "503", description = "업로드 URL을 발급할 수 없음")
    })
    ResponseEntity<AudioUploadResponse> prepare(@Valid AudioUploadRequest request,
            @Parameter(hidden = true) UUID devicePublicId);
}
