package com.relationshiptemperature.api.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        ErrorCode code = exception.errorCode();
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, exception.getMessage(), requestId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), message(error)))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.validation(requestId(request), fields));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        List<ErrorResponse.FieldError> fields = exception.getParameterValidationResults().stream()
                .flatMap(result -> parameterErrors(result).stream())
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.validation(requestId(request), fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<ErrorResponse.FieldError> fields = exception.getConstraintViolations().stream()
                .map(violation -> new ErrorResponse.FieldError(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.validation(requestId(request), fields));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fields = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? List.of(new ErrorResponse.FieldError(mismatch.getName(), "요청 값의 타입이 올바르지 않습니다."))
                : List.of();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.validation(requestId(request), fields));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return error(ErrorCode.METHOD_NOT_ALLOWED, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        return error(ErrorCode.UNSUPPORTED_MEDIA_TYPE, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure requestId={}", requestId(request), exception);
        return ResponseEntity.internalServerError().body(ErrorResponse.of(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.defaultMessage(),
                requestId(request)
        ));
    }

    private List<ErrorResponse.FieldError> parameterErrors(ParameterValidationResult result) {
        if (result instanceof ParameterErrors errors && errors.hasFieldErrors()) {
            return errors.getFieldErrors().stream()
                    .map(error -> new ErrorResponse.FieldError(error.getField(), message(error)))
                    .toList();
        }
        String parameterName = result.getMethodParameter().getParameterName();
        String field = parameterName == null ? "request" : parameterName;
        return result.getResolvableErrors().stream()
                .map(error -> new ErrorResponse.FieldError(field, message(error)))
                .toList();
    }

    private String message(MessageSourceResolvable error) {
        String message = error.getDefaultMessage();
        return message == null || message.isBlank() ? "요청 값이 올바르지 않습니다." : message;
    }

    private ResponseEntity<ErrorResponse> error(ErrorCode code, HttpServletRequest request) {
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, code.defaultMessage(), requestId(request)));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "unknown" : value.toString();
    }
}
