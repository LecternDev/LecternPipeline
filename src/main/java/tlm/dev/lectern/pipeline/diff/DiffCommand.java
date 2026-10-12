package tlm.dev.lectern.pipeline.diff;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public record DiffCommand(Path executable, Duration timeout) {

    public DiffCommand {
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(timeout, "timeout");
    }
}
