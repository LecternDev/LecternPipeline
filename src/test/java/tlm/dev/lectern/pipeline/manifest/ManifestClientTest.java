package tlm.dev.lectern.pipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tlm.dev.lectern.pipeline.fixtures.LocalHttpServer;

class ManifestClientTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Test
    void bodyIsReturnedAsUtf8Text() {
        String body = "{\"versions\": [], \"note\": \"café\"}";
        try (LocalHttpServer server = new LocalHttpServer(200, body)) {
            assertEquals(body, new ManifestClient(http, server.manifestUri(), TIMEOUT).fetchManifest());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 204, 301, 304, 403, 404, 500, 503})
    void anythingButOkIsAFetchFailureNamingTheStatusAndTheUrl(int status) {
        try (LocalHttpServer server = new LocalHttpServer(status, "{}")) {
            ManifestClient client = new ManifestClient(http, server.manifestUri(), TIMEOUT);
            ManifestFetchException failure = assertThrows(ManifestFetchException.class, client::fetchManifest);
            assertTrue(failure.getMessage().contains("HTTP " + status), failure.getMessage());
            assertTrue(failure.getMessage().contains(server.manifestUri().toString()), failure.getMessage());
        }
    }

    @Test
    void unknownPathIsAFetchFailure() {
        try (LocalHttpServer server = new LocalHttpServer(200, "{}")) {
            ManifestClient client = new ManifestClient(http, server.unknownUri(), TIMEOUT);
            ManifestFetchException failure = assertThrows(ManifestFetchException.class, client::fetchManifest);
            assertTrue(failure.getMessage().contains("HTTP 404"), failure.getMessage());
        }
    }

    @Test
    void unreachableServerIsAFetchFailureWithTheCauseKept() throws IOException {
        URI closed;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            closed = URI.create("http://127.0.0.1:" + socket.getLocalPort() + "/manifest");
        }
        ManifestClient client = new ManifestClient(http, closed, TIMEOUT);
        ManifestFetchException failure = assertThrows(ManifestFetchException.class, client::fetchManifest);
        assertTrue(failure.getMessage().contains(closed.toString()), failure.getMessage());
        assertTrue(failure.getCause() instanceof IOException, String.valueOf(failure.getCause()));
    }

    @Test
    void interruptedThreadKeepsItsInterruptFlag() {
        try (LocalHttpServer server = new LocalHttpServer(200, "{}")) {
            ManifestClient client = new ManifestClient(http, server.manifestUri(), TIMEOUT);
            Thread.currentThread().interrupt();
            try {
                assertThrows(ManifestFetchException.class, client::fetchManifest);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }
}
