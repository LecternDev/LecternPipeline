package tlm.dev.lectern.pipeline.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlm.dev.lectern.pipeline.fixtures.FetchFixtures;
import tlm.dev.lectern.pipeline.fixtures.LocalFileServer;

class HttpDownloadClientTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private LocalFileServer server;
    private HttpClient http;
    private HttpDownloadClient client;

    @BeforeEach
    void setUp() {
        server = new LocalFileServer();
        http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        client = new HttpDownloadClient(http, TIMEOUT, TIMEOUT);
    }

    @AfterEach
    void tearDown() {
        http.close();
        server.close();
    }

    @Test
    void bytesAreReadWhole() {
        byte[] body = FetchFixtures.inventedBytes(1, 150_000);
        server.serve("/file", 200, body);
        assertArrayEquals(body, client.readBytes(server.uri("/file"), body.length));
    }

    @Test
    void bytesAboveTheLimitAreRefusedNamingTheLimitAndTheUrl() {
        byte[] body = FetchFixtures.inventedBytes(1, 1000);
        server.serve("/file", 200, body);
        TransferException failure = assertThrows(TransferException.class, () -> client.readBytes(server.uri("/file"), 999));
        assertTrue(failure.getMessage().contains("larger than the 999 bytes"), failure.getMessage());
        assertTrue(failure.getMessage().contains(server.uri("/file").toString()), failure.getMessage());
    }

    @Test
    void bodyIsWrittenIntoTheTargetFile(@TempDir Path directory) throws IOException {
        byte[] body = FetchFixtures.inventedBytes(2, 400_000);
        server.serve("/file", 200, body);
        Path target = Files.createTempFile(directory, "part", ".tmp");
        client.saveTo(server.uri("/file"), target, body.length);
        assertArrayEquals(body, Files.readAllBytes(target));
    }

    @Test
    void saveToTruncatesAnExistingTargetBeforeWriting(@TempDir Path directory) throws IOException {
        byte[] body = FetchFixtures.inventedBytes(2, 10);
        server.serve("/file", 200, body);
        Path target = directory.resolve("part.tmp");
        Files.write(target, new byte[100]);
        client.saveTo(server.uri("/file"), target, 100);
        assertArrayEquals(body, Files.readAllBytes(target));
    }

    @Test
    void emptyBodyGivesAnEmptyArray() {
        server.serve("/empty", 200, new byte[0]);
        assertEquals(0, client.readBytes(server.uri("/empty"), 10).length);
    }

    @Test
    void anyStatusButOkIsAFailureNamingTheStatus() {
        server.serve("/missing", 404, new byte[0]);
        server.serve("/broken", 500, "oops".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        TransferException notFound = assertThrows(TransferException.class, () -> client.readBytes(server.uri("/missing"), 10));
        TransferException broken = assertThrows(TransferException.class, () -> client.readBytes(server.uri("/broken"), 10));
        assertTrue(notFound.getMessage().contains("HTTP 404"), notFound.getMessage());
        assertTrue(broken.getMessage().contains("HTTP 500"), broken.getMessage());
    }

    @Test
    void bodyThatStallsAfterTheHeadersEndsWithATimeout() {
        server.serveThenStall("/stall", new byte[1000]);
        HttpDownloadClient impatient = new HttpDownloadClient(http, TIMEOUT, Duration.ofSeconds(1));
        long start = System.nanoTime();
        TransferException failure = assertThrows(TransferException.class, () -> impatient.readBytes(server.uri("/stall"), 100_000));
        long elapsedSeconds = Duration.ofNanos(System.nanoTime() - start).toSeconds();
        assertTrue(failure.getMessage().contains("did not finish within 1 seconds"), failure.getMessage());
        assertTrue(elapsedSeconds < 5, "gave up after " + elapsedSeconds + " s");
    }

    @Test
    void closedPortIsAFailureWithTheCauseKept() throws IOException {
        URI closed;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            closed = URI.create("http://127.0.0.1:" + socket.getLocalPort() + "/file");
        }
        TransferException failure = assertThrows(TransferException.class, () -> client.readBytes(closed, 10));
        assertTrue(failure.getMessage().contains(closed.toString()), failure.getMessage());
        assertTrue(failure.getCause() instanceof IOException, String.valueOf(failure.getCause()));
    }
}
