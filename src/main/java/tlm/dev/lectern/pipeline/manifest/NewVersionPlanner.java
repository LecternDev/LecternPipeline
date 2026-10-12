package tlm.dev.lectern.pipeline.manifest;

import java.util.List;
import java.util.Set;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class NewVersionPlanner {

    public List<TrackedVersion> plan(List<TrackedVersion> tracked, Set<VersionId> known) {
        return tracked.stream()
                .filter(version -> !known.contains(version.id()))
                .sorted(TrackedVersion.NEWEST_FIRST.reversed())
                .toList();
    }
}
