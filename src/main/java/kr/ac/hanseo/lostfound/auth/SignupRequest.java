package kr.ac.hanseo.lostfound.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 입력 규칙: 학번 숫자 8자리, 비밀번호 8자 이상 영문+숫자, 전화번호 010-0000-0000, 모두 필수 */
public record SignupRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{8}$", message = "숫자 8자리여야 합니다")
        String studentId,

        @NotBlank @Size(min = 8, max = 64, message = "8자 이상이어야 합니다")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).+$", message = "영문과 숫자를 모두 포함해야 합니다")
        String password,

        @NotBlank @Size(max = 30)
        String name,

        @NotBlank @Size(max = 50)
        String department,

        @NotBlank @Pattern(regexp = "^010-[0-9]{4}-[0-9]{4}$", message = "010-0000-0000 형식이어야 합니다")
        String phone) {
}
