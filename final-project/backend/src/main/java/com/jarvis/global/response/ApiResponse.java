package com.jarvis.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 모든 API 응답의 공통 envelope (03 D2).
 * 성공: {"success": true, "data": ...} / 실패: {"success": false, "error": {"code", "message", "fields"?}}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> error(ErrorCode code) {
        return new ApiResponse<>(false, null, new ErrorBody(code.name(), code.getMessage(), null));
    }

    public static ApiResponse<Void> error(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(code.name(), message, null));
    }

    public static ApiResponse<Void> validationError(List<FieldErrorDetail> fields) {
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return new ApiResponse<>(false, null, new ErrorBody(code.name(), code.getMessage(), fields));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(String code, String message, List<FieldErrorDetail> fields) {
    }

    public record FieldErrorDetail(String field, String message) {
    }
}
