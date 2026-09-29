package com.pheeeew.emotion.fixture;

import com.pheeeew.emotion.domain.AudioUpload;
import java.time.Instant;

public final class AudioUploadFixture {

    public static final Instant 만료_시각 = Instant.parse("2026-09-29T00:00:00Z");

    private AudioUploadFixture() {
    }

    public static AudioUpload.AudioUploadBuilder 기본_업로드_빌더() {
        return AudioUpload.builder()
                .deviceId(1L)
                .objectKey("pheeeew/development/audio/recording.m4a")
                .contentType("audio/mp4")
                .contentLength(1024)
                .expiresAt(만료_시각);
    }
}
