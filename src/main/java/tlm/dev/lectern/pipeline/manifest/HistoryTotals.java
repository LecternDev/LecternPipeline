package tlm.dev.lectern.pipeline.manifest;

public record HistoryTotals(
        int versions,
        int oldNaming,
        int newNaming,
        int releases,
        int releaseCandidates,
        int preReleases,
        int snapshots,
        int withStepBase,
        int withReleaseBase) {
}
