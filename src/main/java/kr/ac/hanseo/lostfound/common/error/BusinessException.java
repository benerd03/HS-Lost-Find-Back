package kr.ac.hanseo.lostfound.common.error;

import lombok.Getter;

/**
 * 서비스 계층에서 던지는 예외. GlobalExceptionHandler가 { success:false, code, message }로 바꾼다.
 * 예: throw new BusinessException(ErrorCode.CLAIM_DUPLICATE);
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
