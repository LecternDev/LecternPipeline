package tlm.dev.lectern.pipeline.normalize;

import java.util.Objects;
import tlm.dev.lectern.pipeline.json.CanonicalJsonWriter;
import tlm.dev.lectern.pipeline.json.JsonTextReader;

public record NormalizationTools(TreeScanner scanner, JsonTextReader reader, CanonicalJsonWriter writer) {

    public NormalizationTools {
        Objects.requireNonNull(scanner, "scanner");
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(writer, "writer");
    }
}
