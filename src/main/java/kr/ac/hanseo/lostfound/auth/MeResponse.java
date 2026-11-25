package kr.ac.hanseo.lostfound.auth;

import kr.ac.hanseo.lostfound.common.auth.Role;
import kr.ac.hanseo.lostfound.common.auth.SessionUser;

/** 로그인 응답과 /api/auth/me 응답 (관리자는 department가 소속 부서) */
public record MeResponse(String loginId, String name, Role role, String department) {

    public static MeResponse from(SessionUser user) {
        return new MeResponse(user.loginId(), user.name(), user.role(), user.department());
    }
}
