package tlm.dev.lectern.pipeline;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record PipelineConfig(
        Command command, URI manifestUrl, Path workbench, Path java, Path git, Optional<Path> diffCommand, Path scratchRoot) {

    public PipelineConfig {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(manifestUrl, "manifestUrl");
        Objects.requireNonNull(workbench, "workbench");
        Objects.requireNonNull(java, "java");
        Objects.requireNonNull(git, "git");
        Objects.requireNonNull(diffCommand, "diffCommand");
        Objects.requireNonNull(scratchRoot, "scratchRoot");
    }
}
