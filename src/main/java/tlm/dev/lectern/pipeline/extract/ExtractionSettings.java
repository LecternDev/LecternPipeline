package tlm.dev.lectern.pipeline.extract;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public record ExtractionSettings(Path generatedRoot, Path scratchRoot, Path java, Duration timeout) {

    public ExtractionSettings {
        Objects.requireNonNull(generatedRoot, "generatedRoot");
        Objects.requireNonNull(scratchRoot, "scratchRoot");
        Objects.requireNonNull(java, "java");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("The generator timeout must be positive: " + timeout);
        }
    }
}
