package tlm.dev.lectern.pipeline.manifest;

import java.util.Objects;

public record ListedVersion(TrackedVersion version, DiffBase base) {

    public ListedVersion {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(base, "base");
    }
}
