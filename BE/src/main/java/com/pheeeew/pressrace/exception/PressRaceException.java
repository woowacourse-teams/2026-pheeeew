package com.pheeeew.pressrace.exception;

import com.pheeeew.common.exception.ErrorCode;
import com.pheeeew.common.exception.PheeeewException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class PressRaceException extends PheeeewException {

    private static final String INPUT_CONFLICT = "INPUT_CONFLICT";

    private final String code;
    private final UUID requestId;

    public PressRaceException(String code) {
        this(code, 1, null);
    }

    public PressRaceException(String code, long retryAfterSeconds) {
        this(code, retryAfterSeconds, null);
    }

    public PressRaceException(String code, UUID requestId) {
        this(code, 1, requestId);
    }

    private PressRaceException(String code, long retryAfterSeconds, UUID requestId) {
        super(new RaceError(code), null, INPUT_CONFLICT.equals(code) ? null : Duration.ofSeconds(retryAfterSeconds));
        this.code = code;
        this.requestId = requestId;
    }

    public UUID requestId() {
        return requestId;
    }

    public String code() {
        return code;
    }

    public long retryAfterSeconds() {
        return getRetryAfter() == null ? 0 : getRetryAfter().toSeconds();
    }

    private record RaceError(String code) implements ErrorCode {
        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getMessage() {
            return "프레스 입력을 처리할 수 없습니다.";
        }

        @Override
        public HttpStatus getStatus() {
            return switch (code) {
                case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
                case INPUT_CONFLICT -> HttpStatus.CONFLICT;
                default -> HttpStatus.SERVICE_UNAVAILABLE;
            };
        }
    }
}
