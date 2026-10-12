package tlm.dev.lectern.pipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tlm.dev.lectern.pipeline.fixtures.LocalFileServer;
import tlm.dev.lectern.pipeline.http.HttpDownloadClient;
import tlm.dev.lectern.pipeline.http.TransferException;

class ManifestClientTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final LocalFileServer server = new LocalFileServer();

    @AfterEach
    void tearDown() {
        http.close();
        server.close();
    }

    private ManifestClient client(String path, Duration transferTimeout) {
        return new ManifestClient(new HttpDownloadClient(http, TIMEOUT, transferTimeout), server.uri(path));
    }

    @Test
    void bodyIsReturnedAsUtf8Text() {
        String body = "{\"versions\": [], \"note\": \"caf" + (char) 233 + "\"}";
        server.serve("/manifest", 200, body.getBytes(StandardCharsets.UTF_8));
        assertEquals(body, client("/manifest", TIMEOUT).fetchManifest());
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 204, 301, 304, 403, 404, 500, 503})
    void anythingButOkIsAFetchFailureNamingTheStatusAndTheUrl(int status) {
        server.serve("/manifest", status, "{}".getBytes(StandardCharsets.UTF_8));
        ManifestFetchException failure = assertThrows(ManifestFetchException.class, () -> client("/manifest", TIMEOUT).fetchManifest());
        assertTrue(failure.getMessage().contains("HTTP " + status), failure.getMessage());
        assertTrue(failure.getMessage().contains(server.uri("/manifest").toString()), failure.getMessage());
    }

    @Test
    void unknownPathIsAFetchFailure() {
        server.serve("/manifest", 200, "{}".getBytes(StandardCharsets.UTF_8));
        ManifestFetchException failure = assertThrows(ManifestFetchException.class, () -> client("/other", TIMEOUT).fetchManifest());
        assertTrue(failure.getMessage().contains("HTTP 404"), failure.getMessage());
    }

    @Test
    void unreachableServerIsAFetchFailureWithTheCauseKept() throws IOException {
        URI closed;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            closed = URI.create("http://127.0.0.1:" + socket.getLocalPort() + "/manifest");
        }
        ManifestClient client = new ManifestClient(new HttpDownloadClient(http, TIMEOUT, TIMEOUT), closed);
        ManifestFetchException failure = assertThrows(ManifestFetchException.class, client::fetchManifest);
        assertTrue(failure.getMessage().contains(closed.toString()), failure.getMessage());
        assertTrue(failure.getCause() instanceof TransferException, String.valueOf(failure.getCause()));
        assertTrue(failure.getCause().getCause() instanceof IOException, String.valueOf(failure.getCause().getCause()));
    }

    @Test
    void interruptedThreadKeepsItsInterruptFlag() {
        server.serve("/manifest", 200, "{}".getBytes(StandardCharsets.UTF_8));
        ManifestClient client = client("/manifest", TIMEOUT);
        Thread.currentThread().interrupt();
        try {
            assertThrows(ManifestFetchException.class, client::fetchManifest);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void manifestBodyThatStallsEndsWithATimeoutInsteadOfWaitingForTheServer() {
        server.serveThenStall("/manifest", "{\"versions\": [".getBytes(StandardCharsets.UTF_8));
        long start = System.nanoTime();
        ManifestFetchException failure = assertThrows(ManifestFetchException.class, () -> client("/manifest", Duration.ofSeconds(1)).fetchManifest());
        long elapsedSeconds = Duration.ofNanos(System.nanoTime() - start).toSeconds();
        assertTrue(failure.getMessage().contains("did not finish within 1 seconds"), failure.getMessage());
        assertTrue(elapsedSeconds < 5, "the stalled server answers after 6 seconds, the client gave up after " + elapsedSeconds + " s");
    }

    @Test
    void manifestAboveTheSizeLimitIsRefused() {
        byte[] huge = new byte[17 * 1024 * 1024];
        server.serve("/manifest", 200, huge);
        ManifestFetchException failure = assertThrows(ManifestFetchException.class, () -> client("/manifest", Duration.ofSeconds(30)).fetchManifest());
        assertTrue(failure.getMessage().contains("larger than"), failure.getMessage());
    }
}
