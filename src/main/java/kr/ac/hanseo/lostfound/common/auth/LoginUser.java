package kr.ac.hanseo.lostfound.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 컨트롤러 파라미터에 붙이면 세션의 로그인 사용자를 넣어 준다. 로그인 검사는 AuthInterceptor가 먼저 한다. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
}
