package com.relationshiptemperature.api.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "세션이 만료되었습니다."),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "CSRF 토큰이 올바르지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    RELATIONSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "관계를 찾을 수 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    ANALYSIS_ALREADY_RUNNING(HttpStatus.CONFLICT, "이미 분석이 진행 중입니다."),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "업로드 가능한 최대 파일 크기를 초과했습니다."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 파일 형식입니다."),
    INVALID_KAKAO_EXPORT(HttpStatus.UNPROCESSABLE_CONTENT, "카카오톡 대화 내보내기 파일을 확인할 수 없습니다."),
    GROUP_CHAT_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_CONTENT, "단체 대화는 지원하지 않습니다."),
    SELF_PARTICIPANT_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "본인 참가자 이름이 대화와 일치하지 않습니다."),
    DUPLICATE_CONVERSATION_FILE(HttpStatus.CONFLICT, "같은 관계에 이미 업로드된 대화 파일입니다."),
    CHECK_IN_INCOMPLETE(HttpStatus.UNPROCESSABLE_CONTENT, "체크인 응답이 완전하지 않습니다."),
    REPORT_REQUIRED(HttpStatus.CONFLICT, "완료된 관계 리포트가 필요합니다."),
    CHAT_ALREADY_GENERATING(HttpStatus.CONFLICT, "이미 AI 답변을 생성하고 있습니다."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "현재 AI 상담을 사용할 수 없습니다."),
    ANALYSIS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "현재 분석을 사용할 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
