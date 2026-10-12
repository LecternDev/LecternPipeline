package tlm.dev.lectern.pipeline.manifest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import tlm.dev.lectern.pipeline.http.DownloadSource;
import tlm.dev.lectern.pipeline.http.TransferException;

public final class ManifestClient implements ManifestSource {

    private static final long MANIFEST_LIMIT = 16L * 1024 * 1024;

    private final DownloadSource source;
    private final URI manifestUrl;

    public ManifestClient(DownloadSource source, URI manifestUrl) {
        this.source = Objects.requireNonNull(source, "source");
        this.manifestUrl = Objects.requireNonNull(manifestUrl, "manifestUrl");
    }

    @Override
    public String fetchManifest() {
        try {
            return new String(source.readBytes(manifestUrl, MANIFEST_LIMIT), StandardCharsets.UTF_8);
        } catch (TransferException exception) {
            throw new ManifestFetchException("Could not fetch the manifest from " + manifestUrl + ": " + exception.getMessage(), exception);
        }
    }
}
