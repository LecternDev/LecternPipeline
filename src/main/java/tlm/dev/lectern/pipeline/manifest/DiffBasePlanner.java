package tlm.dev.lectern.pipeline.manifest;

import java.util.List;
import java.util.Optional;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.model.VersionKind;

public final class DiffBasePlanner {

    private record Candidates(Optional<TrackedVersion> step, Optional<TrackedVersion> release) {
    }

    private final List<TrackedVersion> newestFirst;
    private final Optional<TrackedVersion> historyStart;

    public DiffBasePlanner(List<TrackedVersion> tracked) {
        this(tracked, Optional.empty());
    }

    public DiffBasePlanner(List<TrackedVersion> tracked, Optional<VersionId> historyStart) {
        this.newestFirst = tracked.stream().sorted(TrackedVersion.NEWEST_FIRST).toList();
        this.historyStart = historyStart.map(id -> newestFirst.stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new HistoryStartNotFoundException(id)));
    }

    public boolean inHistory(TrackedVersion version) {
        return historyStart.map(start -> TrackedVersion.NEWEST_FIRST.compare(version, start) <= 0).orElse(true);
    }

    public DiffBase baseOf(TrackedVersion version) {
        Candidates candidates = switch (version.form()) {
            case VersionForm.NewNaming form -> newNamingCandidates(version, form.line());
            case VersionForm.OldNaming form -> oldNamingCandidates(version, form.line());
        };
        return new DiffBase(idWithinHistory(candidates.step()), idWithinHistory(candidates.release()));
    }

    private Optional<VersionId> idWithinHistory(Optional<TrackedVersion> candidate) {
        return candidate.filter(this::inHistory).map(TrackedVersion::id);
    }

    private Candidates newNamingCandidates(TrackedVersion version, ReleaseLine line) {
        List<TrackedVersion> earlier = earlierThan(version);
        Optional<TrackedVersion> previousRelease = previousReleaseOfNewNaming(earlier, line);
        Optional<TrackedVersion> step = earlier.stream()
                .filter(candidate -> candidate.form().releaseLine().filter(line::equals).isPresent())
                .findFirst()
                .or(() -> previousRelease);
        return new Candidates(step, isRelease(version) ? previousRelease : Optional.empty());
    }

    private static Optional<TrackedVersion> previousReleaseOfNewNaming(List<TrackedVersion> earlier, ReleaseLine line) {
        Optional<TrackedVersion> sameDrop = line.isPatch()
                ? earlier.stream()
                        .filter(DiffBasePlanner::isRelease)
                        .filter(candidate -> candidate.form().releaseLine().filter(line::sameDrop).isPresent())
                        .findFirst()
                : Optional.empty();
        return sameDrop.or(() -> earlier.stream().filter(DiffBasePlanner::isRelease).findFirst());
    }

    private Candidates oldNamingCandidates(TrackedVersion version, Optional<ReleaseLine> line) {
        List<TrackedVersion> earlier = earlierThan(version).stream()
                .filter(candidate -> candidate.form() instanceof VersionForm.OldNaming)
                .toList();
        Optional<TrackedVersion> previousRelease = earlier.stream().filter(DiffBasePlanner::isRelease).findFirst();
        Optional<TrackedVersion> sameLine = line.flatMap(own -> earlier.stream()
                .filter(candidate -> candidate.form().releaseLine().filter(own::equals).isPresent())
                .findFirst());
        Optional<TrackedVersion> openSnapshot = earlier.stream()
                .takeWhile(candidate -> !previousRelease.equals(Optional.of(candidate)))
                .filter(DiffBasePlanner::isSnapshot)
                .findFirst();
        Optional<TrackedVersion> step = sameLine.or(() -> openSnapshot).or(() -> previousRelease);
        return new Candidates(step, isRelease(version) ? previousRelease : Optional.empty());
    }

    private List<TrackedVersion> earlierThan(TrackedVersion version) {
        return newestFirst.stream()
                .filter(candidate -> TrackedVersion.NEWEST_FIRST.compare(candidate, version) > 0)
                .toList();
    }

    private static boolean isRelease(TrackedVersion version) {
        return version.kind() instanceof VersionKind.Release;
    }

    private static boolean isSnapshot(TrackedVersion version) {
        return version.kind() instanceof VersionKind.Snapshot;
    }
}
