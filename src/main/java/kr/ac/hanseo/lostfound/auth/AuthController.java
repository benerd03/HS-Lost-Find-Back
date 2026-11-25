package kr.ac.hanseo.lostfound.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import kr.ac.hanseo.lostfound.common.api.ApiResponse;
import kr.ac.hanseo.lostfound.common.auth.LoginUser;
import kr.ac.hanseo.lostfound.common.auth.SessionUser;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String SESSION_COOKIE = "JSESSIONID";

    private final AuthService authService;

    /** 가입 후 자동 로그인은 하지 않는다 */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, String>> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(Map.of("studentId", authService.signup(request)));
    }

    @PostMapping("/login")
    public ApiResponse<MeResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        SessionUser user = authService.authenticate(request);
        HttpSession session = httpRequest.getSession(true);
        // 세션 고정 공격 방지: 로그인할 때 세션 ID를 새로 발급
        httpRequest.changeSessionId();
        session.setAttribute(SessionUser.SESSION_KEY, user);
        return ApiResponse.ok(MeResponse.from(user));
    }

    /** 세션 무효화 + 쿠키 삭제. 쿠키를 지워야 다음 요청이 SESSION_EXPIRED가 아닌 LOGIN_REQUIRED가 된다 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        ResponseCookie expired = ResponseCookie.from(SESSION_COOKIE, "")
                .path("/").httpOnly(true).sameSite("Lax").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@LoginUser SessionUser user) {
        return ApiResponse.ok(MeResponse.from(user));
    }
}
