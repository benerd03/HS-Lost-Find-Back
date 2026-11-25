package kr.ac.hanseo.lostfound.common.api;

/** 실패 응답: { "success": false, "code": "CLAIM_DUPLICATE", "message": "..." } */
public record ErrorResponse(boolean success, String code, String message) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(false, code, message);
    }
}
