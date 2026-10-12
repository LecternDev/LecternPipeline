package tlm.dev.lectern.pipeline.manifest;

import java.util.List;

public final class HistoryRenderer {

    private static final String LABEL_VERSIONS = "versions";
    private static final String LABEL_OLD = "old naming";
    private static final String LABEL_NEW = "new naming";
    private static final String LABEL_RELEASE = "release";
    private static final String LABEL_RELEASE_CANDIDATE = "release-candidate";
    private static final String LABEL_PRE_RELEASE = "pre-release";
    private static final String LABEL_SNAPSHOT = "snapshot";
    private static final String LABEL_STEP = "with a step base";
    private static final String LABEL_RELEASE_BASE = "with a release base";

    public String render(History history) {
        HistoryTotals totals = history.totals();
        List<String> labels = List.of(LABEL_VERSIONS, LABEL_OLD, LABEL_NEW, LABEL_RELEASE, LABEL_RELEASE_CANDIDATE, LABEL_PRE_RELEASE, LABEL_SNAPSHOT,
                LABEL_STEP, LABEL_RELEASE_BASE);
        List<Integer> counts = List.of(totals.versions(), totals.oldNaming(), totals.newNaming(), totals.releases(), totals.releaseCandidates(),
                totals.preReleases(), totals.snapshots(), totals.withStepBase(), totals.withReleaseBase());
        int labelWidth = labels.stream().mapToInt(String::length).max().orElse(0);
        int countWidth = counts.stream().mapToInt(count -> Integer.toString(count).length()).max().orElse(0);
        StringBuilder out = new StringBuilder(new VersionListRenderer().render(history.versions()));
        out.append("\ntotals\n");
        for (int index = 0; index < labels.size(); index++) {
            out.append("  ").append(labels.get(index)).append(" ".repeat(labelWidth - labels.get(index).length() + 2))
                    .append(" ".repeat(countWidth - Integer.toString(counts.get(index)).length())).append(counts.get(index)).append('\n');
        }
        return out.toString();
    }
}
