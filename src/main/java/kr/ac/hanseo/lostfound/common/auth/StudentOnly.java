package kr.ac.hanseo.lostfound.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 학생만 호출할 수 있는 API (컨트롤러 클래스 또는 메서드). 관리자가 호출하면 403 FORBIDDEN. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface StudentOnly {
}
