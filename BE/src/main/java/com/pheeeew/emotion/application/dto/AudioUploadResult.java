package com.pheeeew.emotion.application.dto;

import com.pheeeew.emotion.application.AudioUrlIssuer.UploadUrl;
import com.pheeeew.emotion.domain.AudioUpload;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AudioUploadResult(
        String uploadId, String uploadUrl, Instant expiresAt, Map<String, List<String>> headers
) {

    public static AudioUploadResult of(AudioUpload upload, UploadUrl url) {
        return new AudioUploadResult(upload.getUploadId(), url.uploadUrl(), url.expiresAt(), url.headers());
    }
}
