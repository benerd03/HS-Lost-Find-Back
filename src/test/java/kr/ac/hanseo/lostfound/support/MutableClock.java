package kr.ac.hanseo.lostfound.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import kr.ac.hanseo.lostfound.common.time.KstTime;

/** 테스트에서 시간을 앞으로 돌리기 위한 시계 */
public class MutableClock extends Clock {

    private volatile Duration offset = Duration.ZERO;

    public void advance(Duration duration) {
        offset = offset.plus(duration);
    }

    public void reset() {
        offset = Duration.ZERO;
    }

    @Override
    public ZoneId getZone() {
        return KstTime.ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(offset);
    }
}
