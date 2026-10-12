package tlm.dev.lectern.pipeline.manifest;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.model.VersionKind;

public final class VersionScope {

    private final Set<VersionId> excluded;

    public VersionScope(Set<VersionId> excluded) {
        this.excluded = Set.copyOf(excluded);
    }

    public boolean isExcluded(VersionId id) {
        return excluded.contains(id);
    }

    public Optional<VersionForm> formOf(VersionId id) {
        if (excluded.contains(id)) {
            return Optional.empty();
        }
        return VersionFormParser.parse(id);
    }

    public Optional<VersionKind> kindOf(VersionId id) {
        return formOf(id).map(VersionForm::kind);
    }

    public List<TrackedVersion> track(List<ManifestEntry> entries) {
        return entries.stream()
                .flatMap(entry -> formOf(entry.id()).map(form -> new TrackedVersion(entry, form)).stream())
                .sorted(TrackedVersion.NEWEST_FIRST)
                .toList();
    }
}
