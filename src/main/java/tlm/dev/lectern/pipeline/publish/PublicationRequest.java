package tlm.dev.lectern.pipeline.publish;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tlm.dev.lectern.pipeline.model.VersionId;

public record PublicationRequest(VersionId id, Path normalizedFolder, List<String> files, Map<String, byte[]> diffs, Path repository) {

    public PublicationRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(normalizedFolder, "normalizedFolder");
        files = List.copyOf(files);
        diffs = Map.copyOf(diffs);
        Objects.requireNonNull(repository, "repository");
        for (String name : diffs.keySet()) {
            if (!DataRepositoryRules.DIFF_FILES.contains(name)) {
                throw new IllegalArgumentException("Not a diff file name: " + name);
            }
        }
    }

    public List<String> rawFiles() {
        return files.stream().filter(PublicationRequest::isRaw).sorted().toList();
    }

    public boolean hasMeta() {
        return files.contains(DataRepositoryRules.META_FILE);
    }

    private static boolean isRaw(String relative) {
        return DataRepositoryRules.RAW_FOLDERS.stream().anyMatch(folder -> relative.startsWith(folder + "/"));
    }
}
