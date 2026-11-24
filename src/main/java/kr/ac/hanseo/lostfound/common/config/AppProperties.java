package kr.ac.hanseo.lostfound.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml의 app.* 설정 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String uploadDir,
        boolean demoMode,
        String dataDir,
        boolean seedEnabled) {
}
