package tlm.dev.lectern.pipeline.process;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public sealed interface ProcessOutcome {

    String stdout();

    String stderr();

    static String tail(String text, int lines) {
        List<String> all = text.lines().toList();
        return String.join("\n", all.subList(Math.max(0, all.size() - lines), all.size()));
    }

    record Exited(int exitCode, String stdout, String stderr) implements ProcessOutcome {

        public Exited {
            Objects.requireNonNull(stdout, "stdout");
            Objects.requireNonNull(stderr, "stderr");
        }
    }

    record TimedOut(Duration timeout, String stdout, String stderr) implements ProcessOutcome {

        public TimedOut {
            Objects.requireNonNull(timeout, "timeout");
            Objects.requireNonNull(stdout, "stdout");
            Objects.requireNonNull(stderr, "stderr");
        }
    }
}
