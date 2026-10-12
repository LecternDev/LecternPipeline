package tlm.dev.lectern.pipeline.fixtures;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class SteppingClock extends Clock {

    private Instant now;
    private final Duration step;

    public SteppingClock(Instant start, Duration step) {
        this.now = start;
        this.step = step;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        Instant current = now;
        now = now.plus(step);
        return current;
    }
}
