package kr.ac.hanseo.lostfound.common.auth;

import java.io.Serializable;

/**
 * 세션에 저장하는 로그인 정보. loginId는 학생이면 학번, 관리자면 관리자 ID.
 * 컨트롤러에서는 @LoginUser SessionUser user 로 받는다.
 */
public record SessionUser(String loginId, String name, Role role, String department) implements Serializable {

    public static final String SESSION_KEY = "LOGIN_USER";

    public boolean isStudent() {
        return role == Role.STUDENT;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
