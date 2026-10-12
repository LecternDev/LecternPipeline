package tlm.dev.lectern.pipeline.publish;

public final class PublicationReportRenderer {

    public String render(PublicationResult result) {
        return "published " + result.id().value() + "\n"
                + "  repository  " + result.repository() + "\n"
                + "  commit      " + result.commit() + " data: " + result.id().value() + "\n"
                + "  files       " + result.files() + " under " + DataRepositoryRules.versionFolder(result.id().value()) + "/\n"
                + "  pushed      no, nothing is ever pushed by this command\n";
    }
}
