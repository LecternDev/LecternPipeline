package tlm.dev.lectern.pipeline.manifest;

import java.util.List;
import java.util.Optional;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.model.VersionKind;

public final class DiffBasePlanner {

    private final List<TrackedVersion> newestFirst;

    public DiffBasePlanner(List<TrackedVersion> tracked) {
        this.newestFirst = tracked.stream().sorted(TrackedVersion.NEWEST_FIRST).toList();
    }

    public DiffBase baseOf(TrackedVersion version) {
        return version.form().releaseLine()
                .map(line -> baseWithin(version, line))
                .orElseGet(DiffBase::none);
    }

    private DiffBase baseWithin(TrackedVersion version, ReleaseLine line) {
        List<TrackedVersion> earlier = newestFirst.stream()
                .filter(candidate -> TrackedVersion.NEWEST_FIRST.compare(candidate, version) > 0)
                .toList();
        Optional<VersionId> previousRelease = previousRelease(earlier, line);
        Optional<VersionId> step = earlier.stream()
                .filter(candidate -> candidate.form().releaseLine().filter(line::equals).isPresent())
                .findFirst()
                .map(TrackedVersion::id)
                .or(() -> previousRelease);
        Optional<VersionId> release = isRelease(version) ? previousRelease : Optional.empty();
        return new DiffBase(step, release);
    }

    private static Optional<VersionId> previousRelease(List<TrackedVersion> earlier, ReleaseLine line) {
        Optional<TrackedVersion> sameDrop = line.isPatch()
                ? earlier.stream()
                        .filter(DiffBasePlanner::isRelease)
                        .filter(candidate -> candidate.form().releaseLine().filter(line::sameDrop).isPresent())
                        .findFirst()
                : Optional.empty();
        return sameDrop
                .or(() -> earlier.stream().filter(DiffBasePlanner::isRelease).findFirst())
                .map(TrackedVersion::id);
    }

    private static boolean isRelease(TrackedVersion version) {
        return version.kind() instanceof VersionKind.Release;
    }
}
