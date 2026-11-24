package kr.ac.hanseo.lostfound.common.api;

/**
 * 성공 응답: { "success": true, "data": ... }
 * data가 null이어도 필드는 항상 포함한다 (명세 공통 규칙).
 */
public record ApiResponse<T>(boolean success, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null);
    }
}
