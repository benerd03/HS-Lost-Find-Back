package kr.ac.hanseo.lostfound.common.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;
import kr.ac.hanseo.lostfound.common.error.BusinessException;
import kr.ac.hanseo.lostfound.common.error.ErrorCode;

@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && SessionUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        SessionUser user = AuthInterceptor.currentUser(webRequest.getNativeRequest(HttpServletRequest.class));
        if (user == null) {
            throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
        }
        return user;
    }
}
