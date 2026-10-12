package tlm.dev.lectern.pipeline.manifest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

public final class ManifestClient implements ManifestSource {

    private static final int HTTP_OK = 200;

    private final HttpClient http;
    private final URI manifestUrl;
    private final Duration requestTimeout;

    public ManifestClient(HttpClient http, URI manifestUrl, Duration requestTimeout) {
        this.http = Objects.requireNonNull(http, "http");
        this.manifestUrl = Objects.requireNonNull(manifestUrl, "manifestUrl");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    @Override
    public String fetchManifest() {
        HttpRequest request = HttpRequest.newBuilder(manifestUrl)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new ManifestFetchException("Could not fetch the manifest from " + manifestUrl + ": " + exception, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ManifestFetchException("Interrupted while fetching the manifest from " + manifestUrl, exception);
        }
        if (response.statusCode() != HTTP_OK) {
            throw new ManifestFetchException("The manifest at " + manifestUrl + " answered HTTP " + response.statusCode());
        }
        return response.body();
    }
}
