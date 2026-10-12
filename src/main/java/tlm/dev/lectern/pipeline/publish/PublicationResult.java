package tlm.dev.lectern.pipeline.publish;

import java.nio.file.Path;
import java.util.Objects;
import tlm.dev.lectern.pipeline.model.VersionId;

public record PublicationResult(VersionId id, Path repository, int files, String commit) {

    public PublicationResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(commit, "commit");
    }
}
