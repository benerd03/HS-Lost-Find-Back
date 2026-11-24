package kr.ac.hanseo.lostfound.common.time;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

/**
 * 현재 시각은 LocalDateTime.now() 대신 이걸 쓴다.
 * 테스트에서 Clock을 바꿔 "30분 뒤" 같은 상황을 만들 수 있다.
 */
@Component
public class TimeProvider {

    private final Clock clock;

    public TimeProvider(Clock clock) {
        this.clock = clock;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }
}
