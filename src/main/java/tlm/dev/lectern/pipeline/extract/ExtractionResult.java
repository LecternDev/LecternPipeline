package tlm.dev.lectern.pipeline.extract;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import tlm.dev.lectern.pipeline.model.VersionId;

public record ExtractionResult(VersionId id, Path output, TreeSummary summary, Duration duration) {

    public ExtractionResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(duration, "duration");
    }
}
