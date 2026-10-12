package tlm.dev.lectern.pipeline.extract;

import java.time.Duration;
import java.util.Objects;

public sealed interface ProcessOutcome {

    record Exited(int exitCode, String stdoutTail, String stderrTail) implements ProcessOutcome {

        public Exited {
            Objects.requireNonNull(stdoutTail, "stdoutTail");
            Objects.requireNonNull(stderrTail, "stderrTail");
        }
    }

    record TimedOut(Duration timeout, String stdoutTail, String stderrTail) implements ProcessOutcome {

        public TimedOut {
            Objects.requireNonNull(timeout, "timeout");
            Objects.requireNonNull(stdoutTail, "stdoutTail");
            Objects.requireNonNull(stderrTail, "stderrTail");
        }
    }
}
