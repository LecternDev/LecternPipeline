package tlm.dev.lectern.pipeline.manifest;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.model.VersionKind;

public record TrackedVersion(ManifestEntry entry, VersionForm form) {

    public static final Comparator<TrackedVersion> NEWEST_FIRST = Comparator
            .comparing(TrackedVersion::releaseTime, Comparator.<Instant>reverseOrder())
            .thenComparing(version -> version.id().value());

    public TrackedVersion {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(form, "form");
    }

    public VersionId id() {
        return entry.id();
    }

    public Instant releaseTime() {
        return entry.releaseTime();
    }

    public VersionKind kind() {
        return form.kind();
    }
}
