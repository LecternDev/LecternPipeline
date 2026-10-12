package tlm.dev.lectern.pipeline.fetch;

public final class FetchReportRenderer {

    public String render(FetchedJar fetched) {
        return "fetched " + fetched.id().value() + (fetched.downloaded() ? " (downloaded)" : " (already present)") + "\n"
                + "  jar    " + fetched.jar() + "\n"
                + "  bytes  " + fetched.download().size() + "\n"
                + "  sha1   " + fetched.download().sha1().hex() + "\n";
    }
}
