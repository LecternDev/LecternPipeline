package tlm.dev.lectern.pipeline.normalize;

import java.nio.file.Path;
import java.util.Objects;

public record NormalizationSettings(Path generatedRoot, Path normalizedRoot) {

    public NormalizationSettings {
        Objects.requireNonNull(generatedRoot, "generatedRoot");
        Objects.requireNonNull(normalizedRoot, "normalizedRoot");
    }
}
