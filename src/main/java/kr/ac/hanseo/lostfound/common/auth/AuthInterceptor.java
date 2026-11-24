package kr.ac.hanseo.lostfound.common.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kr.ac.hanseo.lostfound.common.error.BusinessException;
import kr.ac.hanseo.lostfound.common.error.ErrorCode;

/**
 * Spring Security 대신 쓰는 권한 검사 (명세 공통 규칙 "세션").
 * 1. 로그인 확인: 세션 쿠키가 있는데 서버 세션이 없으면 SESSION_EXPIRED, 쿠키도 없으면 LOGIN_REQUIRED
 * 2. /api/admin/** 는 관리자만
 * 3. @StudentOnly 가 붙은 API는 학생만
 * 공개 API(회원가입, 로그인, 카테고리, 건물)는 WebConfig에서 제외한다.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        SessionUser user = currentUser(request);
        if (user == null) {
            boolean expired = request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid();
            throw new BusinessException(expired ? ErrorCode.SESSION_EXPIRED : ErrorCode.LOGIN_REQUIRED);
        }
        if (request.getRequestURI().startsWith("/api/admin/") && !user.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (handler instanceof HandlerMethod method && isStudentOnly(method) && !user.isStudent()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return true;
    }

    static SessionUser currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return (SessionUser) session.getAttribute(SessionUser.SESSION_KEY);
    }

    private static boolean isStudentOnly(HandlerMethod method) {
        return method.hasMethodAnnotation(StudentOnly.class)
                || method.getBeanType().isAnnotationPresent(StudentOnly.class);
    }
}
