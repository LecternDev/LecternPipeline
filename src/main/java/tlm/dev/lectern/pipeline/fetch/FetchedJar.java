package tlm.dev.lectern.pipeline.fetch;

import java.nio.file.Path;
import java.util.Objects;
import tlm.dev.lectern.pipeline.model.VersionId;

public record FetchedJar(VersionId id, Path jar, ServerDownload download, boolean downloaded) {

    public FetchedJar {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(jar, "jar");
        Objects.requireNonNull(download, "download");
    }
}
