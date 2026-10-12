package tlm.dev.lectern.pipeline.manifest;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class ExcludedVersionsReader {

    public Set<VersionId> parse(String text) {
        Objects.requireNonNull(text, "text");
        Set<VersionId> excluded = new LinkedHashSet<>();
        List<String> lines = text.lines().toList();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).strip();
            if (line.isEmpty()) {
                continue;
            }
            if (!excluded.add(new VersionId(line))) {
                throw new IllegalArgumentException("Excluded version listed twice on line " + (index + 1) + ": '" + line + "'");
            }
        }
        return Set.copyOf(excluded);
    }
}
