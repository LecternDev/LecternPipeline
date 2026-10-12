package tlm.dev.lectern.pipeline.manifest;

import java.util.List;
import tlm.dev.lectern.pipeline.model.VersionKind;

public record History(List<ListedVersion> versions) {

    public History {
        versions = List.copyOf(versions);
    }

    public HistoryTotals totals() {
        int oldNaming = 0;
        int newNaming = 0;
        int releases = 0;
        int releaseCandidates = 0;
        int preReleases = 0;
        int snapshots = 0;
        int withStepBase = 0;
        int withReleaseBase = 0;
        for (ListedVersion listed : versions) {
            switch (listed.version().form()) {
                case VersionForm.OldNaming _ -> oldNaming++;
                case VersionForm.NewNaming _ -> newNaming++;
            }
            switch (listed.version().kind()) {
                case VersionKind.Release _ -> releases++;
                case VersionKind.ReleaseCandidate _ -> releaseCandidates++;
                case VersionKind.PreRelease _ -> preReleases++;
                case VersionKind.Snapshot _ -> snapshots++;
            }
            withStepBase += listed.base().step().isPresent() ? 1 : 0;
            withReleaseBase += listed.base().release().isPresent() ? 1 : 0;
        }
        return new HistoryTotals(versions.size(), oldNaming, newNaming, releases, releaseCandidates, preReleases, snapshots, withStepBase, withReleaseBase);
    }
}
