package tlm.dev.lectern.pipeline.manifest;

import java.util.Objects;

public record LookedUpVersion(TrackedVersion version, DiffBase base) {

    public LookedUpVersion {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(base, "base");
    }
}
