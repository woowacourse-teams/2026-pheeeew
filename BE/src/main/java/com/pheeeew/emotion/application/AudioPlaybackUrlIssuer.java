package com.pheeeew.emotion.application;

import java.time.Instant;

/**
 * 비공개 녹음 객체의 짧게 유효한 presigned GET URL을 발급한다.
 * 감정 파트가 조회 권한을 확인한 뒤 호출하며, 구현체는 objectKey를 변경하거나 공개 읽기로 열지 않는다.
 * URL과 만료 시각은 응답에만 사용하고 DB에 저장하지 않는다. 발급 실패 시 원인을 담은 예외를 던진다.
 */
public interface AudioPlaybackUrlIssuer {

    PlaybackUrl issue(String objectKey);

    record PlaybackUrl(String playbackUrl, Instant expiresAt) {

        public static PlaybackUrl of(String playbackUrl, Instant expiresAt) {
            return new PlaybackUrl(playbackUrl, expiresAt);
        }
    }
}
