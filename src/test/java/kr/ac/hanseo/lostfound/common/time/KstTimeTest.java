package kr.ac.hanseo.lostfound.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class KstTimeTest {

    private static final LocalDateTime KST_1430 = LocalDateTime.of(2026, 11, 21, 14, 30);

    @Test
    void 시간대_표기가_없으면_한국시간으로_본다() {
        assertThat(KstTime.parse("2026-11-21T14:30:00")).isEqualTo(KST_1430);
    }

    @Test
    void 플러스9시는_그대로() {
        assertThat(KstTime.parse("2026-11-21T14:30:00+09:00")).isEqualTo(KST_1430);
    }

    @Test
    void UTC는_한국시간으로_바꾼다() {
        assertThat(KstTime.parse("2026-11-21T05:30:00Z")).isEqualTo(KST_1430);
        assertThat(KstTime.parse("2026-11-21T05:30:00.000Z")).isEqualTo(KST_1430);
    }

    @Test
    void 응답은_항상_플러스9시를_붙인다() {
        assertThat(KstTime.format(KST_1430)).isEqualTo("2026-11-21T14:30:00+09:00");
    }

    @Test
    void 날짜만_있으면_거부한다() {
        assertThatThrownBy(() -> KstTime.parse("2026-11-21")).isInstanceOf(IllegalArgumentException.class);
    }
}
