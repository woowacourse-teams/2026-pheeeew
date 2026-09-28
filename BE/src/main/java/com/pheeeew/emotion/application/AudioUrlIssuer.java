package com.pheeeew.emotion.application;

import com.pheeeew.emotion.domain.AudioUpload;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 비공개 녹음 객체의 PUT·GET URL을 발급한다. URL은 응답에만 사용하고 DB에 저장하지 않는다.
 * 재생 URL은 호출자가 감정 조회 권한을 확인한 뒤 요청한다.
 * 발급기는 객체 키를 변경하거나 공개 읽기로 열지 않으며 실패 원인을 보존한다.
 */
public interface AudioUrlIssuer {

    UploadUrl issueUpload(AudioUpload upload, Duration validity);

    PlaybackUrl issuePlayback(String objectKey);

    record UploadUrl(String uploadUrl, Instant expiresAt, Map<String, List<String>> headers) {

        public static UploadUrl of(String uploadUrl, Instant expiresAt, Map<String, List<String>> headers) {
            return new UploadUrl(uploadUrl, expiresAt, headers);
        }
    }

    record PlaybackUrl(String playbackUrl, Instant expiresAt) {

        public static PlaybackUrl of(String playbackUrl, Instant expiresAt) {
            return new PlaybackUrl(playbackUrl, expiresAt);
        }
    }
}
