package kr.ac.hanseo.lostfound.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kr.ac.hanseo.lostfound.common.auth.Role;

public record LoginRequest(
        @NotNull Role role,
        @NotBlank String loginId,
        @NotBlank String password) {
}
