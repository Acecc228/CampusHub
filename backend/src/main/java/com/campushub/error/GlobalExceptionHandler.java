package com.campushub.error;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> invalidInput() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_INPUT", "请求格式或参数无效"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(404).body(new ApiError("NOT_FOUND", "资源不存在"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> methodNotAllowed() {
        return ResponseEntity.status(405).body(new ApiError("METHOD_NOT_ALLOWED", "不支持此请求方法"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> databaseUnavailable(DataAccessException exception) {
        log.error("Database request failed; event={} type={}", UUID.randomUUID(), exception.getClass().getSimpleName());
        return ResponseEntity.status(503).body(new ApiError("SERVICE_UNAVAILABLE", "服务暂时不可用"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("Request failed; event={} type={}", UUID.randomUUID(), exception.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(new ApiError("INTERNAL_ERROR", "服务器内部错误"));
    }
}
