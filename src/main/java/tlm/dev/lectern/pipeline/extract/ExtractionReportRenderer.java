package tlm.dev.lectern.pipeline.extract;

import java.util.Locale;

public final class ExtractionReportRenderer {

    public String render(ExtractionResult result) {
        TreeSummary summary = result.summary();
        return "extracted " + result.id().value() + "\n"
                + "  output    " + result.output() + "\n"
                + "  files     " + summary.files() + " (" + summary.filesWithoutCache() + " without .cache)\n"
                + "  bytes     " + summary.bytes() + " (" + summary.bytesWithoutCache() + " without .cache)\n"
                + "  duration  " + String.format(Locale.ROOT, "%.1f s", result.duration().toMillis() / 1000.0) + "\n";
    }
}
