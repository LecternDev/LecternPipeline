package tlm.dev.lectern.pipeline.publish;

public final class DataRepositoryRules {

    public static final String GITATTRIBUTES = "versions/** -text\ndiffs/** -text\n";

    static final String VERSIONS_FOLDER = "versions";

    private DataRepositoryRules() {
    }

    public static String versionFolder(String id) {
        return VERSIONS_FOLDER + "/" + id;
    }
}
