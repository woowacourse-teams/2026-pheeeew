package com.pheeeew.pressrace.exception;

import com.pheeeew.common.exception.ErrorCode;
import com.pheeeew.common.exception.PheeeewException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class PressRaceException extends PheeeewException {

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
        super(new RaceError(code), null, Duration.ofSeconds(retryAfterSeconds));
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
        return getRetryAfter().toSeconds();
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
            return "RATE_LIMITED".equals(code) ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.SERVICE_UNAVAILABLE;
        }
    }
}
