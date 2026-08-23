package com.relationshiptemperature.api.common.error;

import java.util.List;

public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(String code, String message, String requestId, List<FieldError> fields) {}

    public record FieldError(String field, String reason) {}

    public static ErrorResponse of(ErrorCode code, String message, String requestId) {
        return new ErrorResponse(new ErrorBody(code.name(), message, requestId, List.of()));
    }

    public static ErrorResponse validation(String requestId, List<FieldError> fields) {
        return new ErrorResponse(new ErrorBody(
                ErrorCode.INVALID_REQUEST.name(),
                ErrorCode.INVALID_REQUEST.defaultMessage(),
                requestId,
                fields
        ));
    }
}
