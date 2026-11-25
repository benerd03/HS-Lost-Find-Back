package kr.ac.hanseo.lostfound;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LostFoundApplication {

    public static void main(String[] args) {
        // 모든 시간은 한국 시간 기준 (DB DATETIME도 KST로 저장)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(LostFoundApplication.class, args);
    }
}
