package tlm.dev.lectern.pipeline.fetch;

import java.net.URI;
import java.util.Objects;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;

public record VersionJsonRef(VersionId id, URI url, Sha1 sha1) {

    public VersionJsonRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(sha1, "sha1");
    }
}
