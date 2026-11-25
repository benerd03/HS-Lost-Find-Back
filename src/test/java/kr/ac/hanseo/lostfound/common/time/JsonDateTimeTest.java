package kr.ac.hanseo.lostfound.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

/** 실제 스프링의 ObjectMapper로 응답·요청 날짜 규칙을 확인 */
@SpringBootTest
class JsonDateTimeTest {

    @Autowired
    ObjectMapper objectMapper;

    record Sample(LocalDateTime at, LocalDateTime nothing) {
    }

    @Test
    void 응답의_날짜에는_플러스9시가_붙고_null은_필드를_유지한다() throws Exception {
        String json = objectMapper.writeValueAsString(new Sample(LocalDateTime.of(2026, 11, 21, 14, 30, 5, 123), null));

        assertThat(json).isEqualTo("{\"at\":\"2026-11-21T14:30:05+09:00\",\"nothing\":null}");
    }

    @Test
    void 요청은_세_형식을_모두_한국시간으로_받는다() throws Exception {
        LocalDateTime expected = LocalDateTime.of(2026, 11, 21, 14, 30);
        for (String value : new String[] {"2026-11-21T14:30:00", "2026-11-21T14:30:00+09:00", "2026-11-21T05:30:00Z"}) {
            Sample sample = objectMapper.readValue("{\"at\":\"" + value + "\"}", Sample.class);
            assertThat(sample.at()).as(value).isEqualTo(expected);
        }
    }

    @Test
    void 잘못된_날짜는_형식_오류() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"at\":\"어제\"}", Sample.class))
                .isInstanceOf(InvalidFormatException.class);
    }
}
