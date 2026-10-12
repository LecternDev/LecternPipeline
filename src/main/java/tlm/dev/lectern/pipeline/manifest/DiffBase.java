package tlm.dev.lectern.pipeline.manifest;

import java.util.Objects;
import java.util.Optional;
import tlm.dev.lectern.pipeline.model.VersionId;

public record DiffBase(Optional<VersionId> step, Optional<VersionId> release) {

    public DiffBase {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(release, "release");
    }

    public static DiffBase none() {
        return new DiffBase(Optional.empty(), Optional.empty());
    }
}
