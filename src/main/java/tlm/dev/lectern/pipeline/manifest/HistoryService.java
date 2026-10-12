package tlm.dev.lectern.pipeline.manifest;

import java.util.List;
import java.util.Objects;

public final class HistoryService {

    private final ManifestSource source;
    private final ManifestReader reader;
    private final VersionScope scope;

    public HistoryService(ManifestSource source, ManifestReader reader, VersionScope scope) {
        this.source = Objects.requireNonNull(source, "source");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    public History history() {
        List<TrackedVersion> tracked = scope.track(reader.read(source.fetchManifest()));
        DiffBasePlanner planner = scope.plannerFor(tracked);
        return new History(tracked.stream()
                .filter(planner::inHistory)
                .sorted(TrackedVersion.NEWEST_FIRST.reversed())
                .map(version -> new ListedVersion(version, planner.baseOf(version)))
                .toList());
    }
}
