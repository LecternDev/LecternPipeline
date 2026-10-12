package tlm.dev.lectern.pipeline.manifest;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;

public record ManifestEntry(VersionId id, String type, URI url, Instant releaseTime, Sha1 sha1) {

    public ManifestEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(releaseTime, "releaseTime");
        Objects.requireNonNull(sha1, "sha1");
        if (type.isBlank()) {
            throw new IllegalArgumentException("A manifest type must not be blank");
        }
        if (!url.isAbsolute()) {
            throw new IllegalArgumentException("A manifest url must be absolute: '" + url + "'");
        }
    }
}
