package com.facecook.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    VALIDATION(HttpStatus.BAD_REQUEST, "요청값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    SUSPENDED(HttpStatus.FORBIDDEN, "정지된 계정입니다."),
    TERMS_REQUIRED(HttpStatus.BAD_REQUEST, "필수 약관에 동의해주세요."),
    CODE_INVALID(HttpStatus.BAD_REQUEST, "인증코드가 올바르지 않습니다."),
    CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증코드가 만료되었습니다. 다시 요청해주세요."),
    RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "인증코드는 30초 후 다시 요청할 수 있습니다."),
    EMAIL_SEND_FAILED(HttpStatus.BAD_GATEWAY, "인증메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    PROFILE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 프로필이 등록되어 있습니다."),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    SELF(HttpStatus.BAD_REQUEST, "자기 자신에게 콕을 보낼 수 없습니다."),
    ALREADY_MATCHED(HttpStatus.CONFLICT, "이미 매칭된 상대입니다."),
    DUPLICATE(HttpStatus.CONFLICT, "이미 콕을 보낸 상대입니다."),
    DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘 보낼 수 있는 콕을 모두 사용했습니다."),
    EVENT_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "행사 전체 콕 제한에 도달했습니다."),
    CLOSED(HttpStatus.FORBIDDEN, "채팅 운영시간이 아닙니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "신고를 찾을 수 없습니다."),
    REPORT_ALREADY_REVIEWED(HttpStatus.CONFLICT, "이미 처리된 신고입니다.");

    private final HttpStatus status;
    private final String message;
}
