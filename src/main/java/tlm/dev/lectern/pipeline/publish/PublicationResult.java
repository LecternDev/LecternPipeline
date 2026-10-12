package tlm.dev.lectern.pipeline.publish;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import tlm.dev.lectern.pipeline.model.VersionId;

public record PublicationResult(VersionId id, Path repository, String commit, int rawFiles, TreeChanges raw, List<String> diffFiles) {

    public PublicationResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(commit, "commit");
        Objects.requireNonNull(raw, "raw");
        diffFiles = List.copyOf(diffFiles);
    }

    public String tag() {
        return id.value();
    }
}
