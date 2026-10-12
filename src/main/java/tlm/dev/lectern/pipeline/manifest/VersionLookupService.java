package tlm.dev.lectern.pipeline.manifest;

import java.util.List;
import java.util.Objects;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class VersionLookupService {

    private final ManifestSource source;
    private final ManifestReader reader;
    private final VersionScope scope;

    public VersionLookupService(ManifestSource source, ManifestReader reader, VersionScope scope) {
        this.source = Objects.requireNonNull(source, "source");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    public TrackedVersion find(VersionId id) {
        return findWithBase(id).version();
    }

    public LookedUpVersion findWithBase(VersionId id) {
        List<ManifestEntry> entries = reader.read(source.fetchManifest());
        ManifestEntry entry = entries.stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new VersionNotFoundException(id));
        if (scope.isExcluded(id)) {
            throw VersionOutOfScopeException.excluded(id);
        }
        VersionForm form = scope.formOf(id).orElseThrow(() -> VersionOutOfScopeException.unknownShape(id));
        TrackedVersion version = new TrackedVersion(entry, form);
        return new LookedUpVersion(version, new DiffBasePlanner(scope.track(entries)).baseOf(version));
    }
}
