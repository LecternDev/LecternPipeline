package tlm.dev.lectern.pipeline.normalize;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import tlm.dev.lectern.pipeline.model.VersionId;

public record NormalizationResult(
        VersionId id,
        Path output,
        List<String> files,
        long bytesRead,
        long bytesWritten,
        int rewritten,
        Duration duration) {

    public NormalizationResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(output, "output");
        files = List.copyOf(files);
        Objects.requireNonNull(duration, "duration");
    }
}
