package tlm.dev.lectern.pipeline.publish;

import java.util.List;

public final class DataRepositoryRules {

    public static final String REPORTS_FOLDER = "reports";
    public static final String DATA_FOLDER = "data";
    public static final String VERSIONS_FOLDER = "versions";
    public static final String DIFFS_FOLDER = "diffs";
    public static final String META_FILE = "meta.json";
    public static final String STEP_DIFF_FILE = "step.json";
    public static final String RELEASE_DIFF_FILE = "release.json";

    public static final List<String> RAW_FOLDERS = List.of(REPORTS_FOLDER, DATA_FOLDER);
    public static final List<String> DIFF_FILES = List.of(STEP_DIFF_FILE, RELEASE_DIFF_FILE);
    public static final String GITATTRIBUTES = "reports/** -text\ndata/** -text\nversions/** -text\ndiffs/** -text\n";

    private DataRepositoryRules() {
    }

    public static String versionFolder(String id) {
        return VERSIONS_FOLDER + "/" + id;
    }

    public static String metaPath(String id) {
        return versionFolder(id) + "/" + META_FILE;
    }

    public static String diffsFolder(String id) {
        return DIFFS_FOLDER + "/" + id;
    }

    public static String diffPath(String id, String file) {
        return diffsFolder(id) + "/" + file;
    }

    public static String commitTitle(String id) {
        return "data: " + id;
    }

    public static List<String> attributeProbePaths(String id) {
        return List.of(
                REPORTS_FOLDER + "/probe.json",
                DATA_FOLDER + "/probe.json",
                metaPath(id),
                diffPath(id, STEP_DIFF_FILE));
    }
}
