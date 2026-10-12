package tlm.dev.lectern.pipeline.diff;

import java.nio.file.Path;
import java.util.Objects;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.model.VersionKind;

public record DiffSide(VersionId id, VersionKind kind, Path folder) {

    public DiffSide {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(folder, "folder");
    }
}
