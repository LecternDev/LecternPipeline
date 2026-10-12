package tlm.dev.lectern.pipeline.publish;

import java.util.stream.Collectors;

public final class PublicationReportRenderer {

    public String render(PublicationResult result) {
        String id = result.id().value();
        String diffs = result.diffFiles().isEmpty()
                ? "none"
                : result.diffFiles().stream().map(file -> DataRepositoryRules.diffPath(id, file)).collect(Collectors.joining(", "));
        return "published " + id + "\n"
                + "  repository  " + result.repository() + "\n"
                + "  commit      " + result.commit() + " " + DataRepositoryRules.commitTitle(id) + "\n"
                + "  tag         " + result.tag() + "\n"
                + "  raw files   " + result.rawFiles() + " (" + result.raw().added() + " added, " + result.raw().modified() + " changed, "
                + result.raw().removed() + " removed)\n"
                + "  version     " + DataRepositoryRules.metaPath(id) + "\n"
                + "  diffs       " + diffs + "\n"
                + "  pushed      no, nothing is ever pushed by this command\n";
    }
}
