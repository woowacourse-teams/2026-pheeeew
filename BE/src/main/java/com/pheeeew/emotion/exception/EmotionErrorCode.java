package com.pheeeew.emotion.exception;

import com.pheeeew.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum EmotionErrorCode implements ErrorCode {

    EMOTION_SAVE_FAILED("EMOTION-007", "감정을 저장하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    EMOTION_NOT_FOUND("SIGH-002", "한숨을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    EMOTION_INVALID_CURSOR("EMOTION-008", "감정 목록 커서를 사용할 수 없습니다.", HttpStatus.BAD_REQUEST),
    EMOTION_REQUEST_ID_CONFLICT("EMOTION-001", "요청 식별자를 사용할 수 없습니다.", HttpStatus.CONFLICT),
    EMOTION_NOT_VISIBLE("EMOTION-002", "감정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    EMOTION_AUDIO_UPLOAD_NOT_FOUND("EMOTION-003", "녹음 업로드를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    EMOTION_AUDIO_UPLOAD_NOT_READY("EMOTION-004", "녹음 업로드가 완료되지 않았습니다.", HttpStatus.CONFLICT),
    EMOTION_AUDIO_UPLOAD_ALREADY_USED("EMOTION-005", "이미 다른 감정에 사용된 녹음입니다.", HttpStatus.CONFLICT),
    EMOTION_AUDIO_PLAYBACK_UNAVAILABLE("EMOTION-009", "녹음 재생 주소를 발급할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    EMOTION_AUDIO_UPLOAD_UNAVAILABLE("EMOTION-006", "녹음 업로드를 확인할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
