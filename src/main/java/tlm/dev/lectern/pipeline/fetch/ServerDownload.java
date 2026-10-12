package tlm.dev.lectern.pipeline.fetch;

import java.net.URI;
import java.util.Objects;
import tlm.dev.lectern.pipeline.model.Sha1;

public record ServerDownload(Sha1 sha1, long size, URI url) {

    public ServerDownload {
        Objects.requireNonNull(sha1, "sha1");
        Objects.requireNonNull(url, "url");
        if (size < 1) {
            throw new IllegalArgumentException("The size of a server jar must be positive: " + size);
        }
    }
}
