package kr.ac.hanseo.lostfound.common.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** API 명세서 "에러 코드" 표와 1:1. 새 코드는 명세서에 먼저 추가한다. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "이미지를 첨부해 주세요."),
    IMAGE_INVALID(HttpStatus.BAD_REQUEST, "jpg·png 이미지, 5MB 이하만 올릴 수 있습니다."),
    FUTURE_DATE(HttpStatus.BAD_REQUEST, "미래 날짜는 입력할 수 없습니다."),
    LOCATION_REQUIRED(HttpStatus.BAD_REQUEST, "현재 위치 또는 건물을 보내 주세요."),
    LOST_ITEM_INVALID(HttpStatus.BAD_REQUEST, "연결할 수 없는 분실 신고입니다."),

    // 401
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    HANDOVER_NOT_PARTY(HttpStatus.FORBIDDEN, "전달 당사자가 아닙니다."),

    // 404
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),

    // 409
    STUDENT_ID_DUPLICATE(HttpStatus.CONFLICT, "이미 가입된 학번입니다."),
    ITEM_LOCKED(HttpStatus.CONFLICT, "수령 신청이 들어온 습득물은 수정·삭제할 수 없습니다."),
    CLAIM_OWN_ITEM(HttpStatus.CONFLICT, "본인이 등록한 습득물에는 신청할 수 없습니다."),
    CLAIM_DUPLICATE(HttpStatus.CONFLICT, "이미 신청한 물품입니다."),
    CLAIM_CLOSED(HttpStatus.CONFLICT, "이미 주인이 확정된 물품입니다."),
    CLAIM_NOT_PENDING(HttpStatus.CONFLICT, "대기 상태의 신청만 처리할 수 있습니다."),
    OWNER_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 주인이 확정된 습득물입니다."),
    HANDOVER_DONE(HttpStatus.CONFLICT, "이미 수령이 완료된 전달입니다."),
    HANDOVER_PLACE_SET(HttpStatus.CONFLICT, "장소가 이미 정해졌습니다. 다시 정하기를 먼저 눌러 주세요."),
    HANDOVER_ALREADY_READY(HttpStatus.CONFLICT, "이미 이동 가능 상태입니다."),
    LOST_ITEM_CLOSED(HttpStatus.CONFLICT, "종료된 분실 신고입니다."),
    LOST_ITEM_HAS_CLAIM(HttpStatus.CONFLICT, "진행 중인 수령 신청이 연결된 분실 신고는 삭제할 수 없습니다."),

    // 명세 외 (프레임워크 오류용)
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;
}
