package tlm.dev.lectern.pipeline.manifest;

import java.util.List;
import java.util.Objects;

public final class RecentVersionsService {

    private final ManifestSource source;
    private final ManifestReader reader;
    private final VersionScope scope;

    public RecentVersionsService(ManifestSource source, ManifestReader reader, VersionScope scope) {
        this.source = Objects.requireNonNull(source, "source");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    public List<ListedVersion> recent(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("The number of versions to list must be at least 1: " + count);
        }
        List<TrackedVersion> tracked = scope.track(reader.read(source.fetchManifest()));
        DiffBasePlanner planner = new DiffBasePlanner(tracked);
        return tracked.stream()
                .limit(count)
                .map(version -> new ListedVersion(version, planner.baseOf(version)))
                .toList();
    }
}
