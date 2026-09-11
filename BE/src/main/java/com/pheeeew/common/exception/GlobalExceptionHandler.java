package com.pheeeew.common.exception;

import static com.pheeeew.common.exception.CommonErrorCode.ENDPOINT_NOT_FOUND;
import static com.pheeeew.common.exception.CommonErrorCode.INTERNAL_SERVER_ERROR;
import static com.pheeeew.common.exception.CommonErrorCode.INVALID_REQUEST;
import static com.pheeeew.common.logging.RequestLogWriter.ERROR_CODE_ATTRIBUTE;
import static com.pheeeew.common.logging.RequestLogWriter.FAILURE_ATTRIBUTE;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PheeeewException.class)
    public ResponseEntity<ErrorResponse> handlePheeeewException(PheeeewException exception, HttpServletRequest request) {
        if (exception.getErrorCode().getStatus().is5xxServerError()) {
            recordFailure(request, exception, exception.getErrorCode());
        }
        return toResponseEntity(exception.getErrorCode());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        return toResponseEntity(INVALID_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException exception) {
        return toResponseEntity(ENDPOINT_NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        recordFailure(request, exception, INTERNAL_SERVER_ERROR);
        return toResponseEntity(INTERNAL_SERVER_ERROR);
    }

    private void recordFailure(HttpServletRequest request, Exception exception, ErrorCode errorCode) {
        request.setAttribute(FAILURE_ATTRIBUTE, exception);
        request.setAttribute(ERROR_CODE_ATTRIBUTE, errorCode.getCode());
    }

    private ResponseEntity<ErrorResponse> toResponseEntity(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode));
    }
}
