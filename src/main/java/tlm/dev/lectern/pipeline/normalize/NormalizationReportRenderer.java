package tlm.dev.lectern.pipeline.normalize;

import java.util.Locale;

public final class NormalizationReportRenderer {

    public String render(NormalizationResult result) {
        return "normalized " + result.id().value() + "\n"
                + "  output    " + result.output() + "\n"
                + "  files     " + result.files().size() + " (" + result.rewritten() + " with other bytes than the generator wrote), plus meta.json\n"
                + "  bytes     " + result.bytesRead() + " read, " + result.bytesWritten() + " written\n"
                + "  duration  " + String.format(Locale.ROOT, "%.1f s", result.duration().toMillis() / 1000.0) + "\n";
    }
}
