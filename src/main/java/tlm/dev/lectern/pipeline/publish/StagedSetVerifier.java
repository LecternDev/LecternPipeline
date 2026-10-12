package tlm.dev.lectern.pipeline.publish;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class StagedSetVerifier {

    static final String ADDED = "A";
    static final String MODIFIED = "M";
    static final String DELETED = "D";

    private static final int EXAMPLES = 5;

    private StagedSetVerifier() {
    }

    public static void verify(VersionId id, String what, TreePlan plan, Set<String> extraAdded, StagedSet staged) {
        Set<String> expectedAdded = new HashSet<>(plan.added());
        expectedAdded.addAll(extraAdded);
        Set<String> removable = plan.removed();
        Set<String> modifiable = plan.kept();
        List<String> unexpected = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> entry : staged.statuses().entrySet()) {
            if (!isExpected(entry.getValue(), entry.getKey(), expectedAdded, removable, modifiable)) {
                unexpected.add(entry.getValue() + " " + entry.getKey());
            }
        }
        expectedAdded.stream().filter(path -> !ADDED.equals(staged.statuses().get(path))).forEach(path -> missing.add(ADDED + " " + path));
        removable.stream().filter(path -> !DELETED.equals(staged.statuses().get(path))).forEach(path -> missing.add(DELETED + " " + path));
        if (!unexpected.isEmpty() || !missing.isEmpty()) {
            throw new PublicationException("Version " + id.value() + ": the " + what + " files are not exactly the published ones. Unexpected "
                    + unexpected.size() + " " + unexpected.stream().sorted().limit(EXAMPLES).toList()
                    + ", missing " + missing.size() + " " + missing.stream().sorted().limit(EXAMPLES).toList());
        }
    }

    public static void verifySame(VersionId id, StagedSet staged, StagedSet committed) {
        if (staged.equals(committed)) {
            return;
        }
        List<String> unexpected = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        committed.statuses().forEach((path, status) -> {
            if (!status.equals(staged.statuses().get(path))) {
                unexpected.add(status + " " + path);
            }
        });
        staged.statuses().forEach((path, status) -> {
            if (!status.equals(committed.statuses().get(path))) {
                missing.add(status + " " + path);
            }
        });
        throw new PublicationException("Version " + id.value() + ": the committed files are not exactly the staged ones. Unexpected "
                + unexpected.size() + " " + unexpected.stream().sorted().limit(EXAMPLES).toList()
                + ", missing " + missing.size() + " " + missing.stream().sorted().limit(EXAMPLES).toList());
    }

    private static boolean isExpected(String status, String path, Set<String> added, Set<String> removable, Set<String> modifiable) {
        return switch (status) {
            case ADDED -> added.contains(path);
            case DELETED -> removable.contains(path);
            case MODIFIED -> modifiable.contains(path);
            default -> false;
        };
    }
}
