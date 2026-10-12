package tlm.dev.lectern.pipeline;

import java.net.URI;
import java.util.Objects;

public record PipelineConfig(URI manifestUrl) {

    public PipelineConfig {
        Objects.requireNonNull(manifestUrl, "manifestUrl");
    }
}
