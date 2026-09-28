package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.application.dto.AudioUploadResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AudioUploadResponse(
        @Schema(description = "업로드 완료 후 감정 등록·수정의 audioUploadId로 전달할 식별자입니다. 객체 키가 아닙니다.")
        String uploadId,

        @Schema(description = "녹음 파일 본문을 PUT으로 전송할 임시 URL입니다. multipart 형식을 사용하지 않습니다.")
        String uploadUrl,

        @Schema(description = "PUT URL의 만료 시각입니다. 감정 연결 가능 기한과 다릅니다.")
        Instant expiresAt,

        @Schema(description = "S3 PUT 요청에 그대로 포함할 헤더입니다. 각 이름에 대응하는 값 목록을 전달합니다.")
        Map<String, List<String>> headers
) {

    public static AudioUploadResponse from(AudioUploadResult result) {
        return new AudioUploadResponse(result.uploadId(), result.uploadUrl(), result.expiresAt(), result.headers());
    }
}
