package kr.ac.hanseo.lostfound.common.config;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import kr.ac.hanseo.lostfound.common.auth.AuthInterceptor;
import kr.ac.hanseo.lostfound.common.auth.LoginUserArgumentResolver;
import kr.ac.hanseo.lostfound.common.time.KstTime;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    /** 로그인 없이 부를 수 있는 API (명세 "인증·공통 데이터" 표의 "누구나") */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/signup",
            "/api/auth/login",
            "/api/categories",
            "/api/buildings");

    private final AuthInterceptor authInterceptor;
    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final AppProperties appProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(PUBLIC_PATHS);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }

    /** 쿼리 파라미터와 multipart 폼 필드(foundAt 등)도 JSON과 같은 날짜 규칙으로 받는다 */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new Converter<String, LocalDateTime>() {
            @Override
            public LocalDateTime convert(String source) {
                return source.isBlank() ? null : KstTime.parse(source);
            }
        });
    }

    /** 업로드 이미지: /uploads/파일명 → app.upload-dir 폴더 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(appProperties.uploadDir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
