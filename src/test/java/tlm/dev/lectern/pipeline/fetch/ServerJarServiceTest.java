package tlm.dev.lectern.pipeline.fetch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlm.dev.lectern.pipeline.fixtures.FetchFixtures;
import tlm.dev.lectern.pipeline.fixtures.LocalFileServer;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;

class ServerJarServiceTest {

    private static final VersionId ID = new VersionId("99.1-rc-1");
    private static final byte[] JAR = FetchFixtures.inventedBytes(7, 300_000);

    @TempDir
    Path workbench;

    private LocalFileServer server;
    private HttpClient http;
    private Path jarsRoot;
    private ServerJarService service;

    @BeforeEach
    void setUp() {
        server = new LocalFileServer();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        jarsRoot = workbench.resolve("jars");
        service = new ServerJarService(
                new HttpDownloadClient(http, Duration.ofSeconds(5), Duration.ofSeconds(30)),
                new JarStore(jarsRoot),
                new VersionJsonReader(new JsonTextReader()),
                new Sha1Hasher());
    }

    @AfterEach
    void tearDown() {
        http.close();
        server.close();
    }

    private VersionJsonRef publish(byte[] jarOnServer, String jarSha1, long jarSize) {
        server.serve("/jar", 200, jarOnServer);
        byte[] versionJson = FetchFixtures.versionJson(ID.value(), jarSha1, jarSize, server.uri("/jar").toString());
        server.serve("/version", 200, versionJson);
        return new VersionJsonRef(ID, server.uri("/version"), new Sha1(FetchFixtures.sha1Hex(versionJson)));
    }

    private VersionJsonRef publishValidJar() {
        return publish(JAR, FetchFixtures.sha1Hex(JAR), JAR.length);
    }

    private List<String> filesUnderJars() throws IOException {
        if (!Files.exists(jarsRoot)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(jarsRoot)) {
            return walk.filter(Files::isRegularFile).map(path -> jarsRoot.relativize(path).toString().replace('\\', '/')).sorted().toList();
        }
    }

    @Test
    void jarIsDownloadedVerifiedAndMovedIntoItsVersionFolder() throws IOException {
        FetchedJar fetched = service.ensure(publishValidJar());
        assertTrue(fetched.downloaded());
        assertEquals(jarsRoot.resolve("99.1-rc-1").resolve("server.jar"), fetched.jar());
        assertArrayEquals(JAR, Files.readAllBytes(fetched.jar()));
        assertEquals(JAR.length, fetched.download().size());
        assertEquals(List.of("99.1-rc-1/server.jar"), filesUnderJars());
    }

    @Test
    void jarAlreadyPresentWithTheRightHashIsNotDownloadedAgain() throws IOException {
        VersionJsonRef ref = publishValidJar();
        service.ensure(ref);
        assertEquals(1, server.hits("/jar"));
        FetchedJar second = service.ensure(ref);
        assertFalse(second.downloaded());
        assertEquals(1, server.hits("/jar"));
        assertEquals(2, server.hits("/version"));
        assertEquals(List.of("99.1-rc-1/server.jar"), filesUnderJars());
    }

    @Test
    void jarAlreadyPresentWithAWrongHashStopsAndIsLeftUntouched() throws IOException {
        VersionJsonRef ref = publishValidJar();
        Path jar = jarsRoot.resolve("99.1-rc-1").resolve("server.jar");
        Files.createDirectories(jar.getParent());
        byte[] other = FetchFixtures.inventedBytes(8, JAR.length);
        Files.write(jar, other);
        IntegrityException failure = assertThrows(IntegrityException.class, () -> service.ensure(ref));
        assertTrue(failure.getMessage().contains("99.1-rc-1"), failure.getMessage());
        assertTrue(failure.getMessage().contains(FetchFixtures.sha1Hex(JAR)), failure.getMessage());
        assertTrue(failure.getMessage().contains(FetchFixtures.sha1Hex(other)), failure.getMessage());
        assertArrayEquals(other, Files.readAllBytes(jar));
        assertEquals(0, server.hits("/jar"));
    }

    @Test
    void jarAlreadyPresentWithAWrongSizeStopsAndIsLeftUntouched() throws IOException {
        VersionJsonRef ref = publishValidJar();
        Path jar = jarsRoot.resolve("99.1-rc-1").resolve("server.jar");
        Files.createDirectories(jar.getParent());
        Files.write(jar, new byte[] {1, 2, 3});
        IntegrityException failure = assertThrows(IntegrityException.class, () -> service.ensure(ref));
        assertTrue(failure.getMessage().contains("Expected " + JAR.length + " bytes, found 3 bytes"), failure.getMessage());
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(jar));
    }

    @Test
    void wrongVersionJsonHashStopsBeforeAnyJarRequest() throws IOException {
        VersionJsonRef good = publishValidJar();
        VersionJsonRef wrong = new VersionJsonRef(ID, good.url(), new Sha1("1".repeat(40)));
        IntegrityException failure = assertThrows(IntegrityException.class, () -> service.ensure(wrong));
        assertTrue(failure.getMessage().contains("99.1-rc-1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("version JSON"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Expected " + "1".repeat(40)), failure.getMessage());
        assertTrue(failure.getMessage().contains("received " + good.sha1().hex()), failure.getMessage());
        assertEquals(0, server.hits("/jar"));
        assertEquals(List.of(), filesUnderJars());
    }

    @Test
    void wrongJarHashStopsNamesBothHashesAndLeavesNoFile() throws IOException {
        byte[] served = FetchFixtures.inventedBytes(9, JAR.length);
        VersionJsonRef ref = publish(served, FetchFixtures.sha1Hex(JAR), JAR.length);
        IntegrityException failure = assertThrows(IntegrityException.class, () -> service.ensure(ref));
        assertTrue(failure.getMessage().contains("99.1-rc-1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("server jar"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Expected " + FetchFixtures.sha1Hex(JAR)), failure.getMessage());
        assertTrue(failure.getMessage().contains("received " + FetchFixtures.sha1Hex(served)), failure.getMessage());
        assertEquals(List.of(), filesUnderJars());
    }

    @Test
    void truncatedJarStopsNamesBothSizesAndLeavesNoFile() throws IOException {
        byte[] truncated = java.util.Arrays.copyOf(JAR, JAR.length - 10);
        VersionJsonRef ref = publish(truncated, FetchFixtures.sha1Hex(JAR), JAR.length);
        IntegrityException failure = assertThrows(IntegrityException.class, () -> service.ensure(ref));
        assertTrue(failure.getMessage().contains("99.1-rc-1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Expected " + JAR.length + " bytes, received " + truncated.length + " bytes"), failure.getMessage());
        assertEquals(List.of(), filesUnderJars());
    }

    @Test
    void slightlyLargerJarStopsNamesBothSizesAndLeavesNoFile() throws IOException {
        byte[] larger = java.util.Arrays.copyOf(JAR, JAR.length + 10);
        VersionJsonRef ref = publish(larger, FetchFixtures.sha1Hex(JAR), JAR.length);
        IntegrityException failure = assertThrows(IntegrityException.class, () -> service.ensure(ref));
        assertTrue(failure.getMessage().contains("Expected " + JAR.length + " bytes, received " + larger.length + " bytes"), failure.getMessage());
        assertEquals(List.of(), filesUnderJars());
    }

    @Test
    void hugelyLargerJarIsCutShortAndLeavesNoFile() throws IOException {
        byte[] huge = new byte[JAR.length + 3 * 1024 * 1024];
        VersionJsonRef ref = publish(huge, FetchFixtures.sha1Hex(JAR), JAR.length);
        DownloadException failure = assertThrows(DownloadException.class, () -> service.ensure(ref));
        assertTrue(failure.getMessage().contains("99.1-rc-1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("larger than"), failure.getMessage());
        assertEquals(List.of(), filesUnderJars());
    }

    @Test
    void missingJarOnTheServerNamesTheVersionTheUrlAndTheStatus() throws IOException {
        byte[] versionJson = FetchFixtures.versionJson(ID.value(), FetchFixtures.sha1Hex(JAR), JAR.length, server.uri("/absent").toString());
        server.serve("/version", 200, versionJson);
        server.serve("/absent", 404, new byte[0]);
        VersionJsonRef ref = new VersionJsonRef(ID, server.uri("/version"), new Sha1(FetchFixtures.sha1Hex(versionJson)));
        DownloadException failure = assertThrows(DownloadException.class, () -> service.ensure(ref));
        assertTrue(failure.getMessage().contains("99.1-rc-1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("HTTP 404"), failure.getMessage());
        assertTrue(failure.getMessage().contains(server.uri("/absent").toString()), failure.getMessage());
        assertEquals(List.of(), filesUnderJars());
    }

    @Test
    void missingVersionJsonOnTheServerIsADownloadFailure() throws IOException {
        server.serve("/version", 500, new byte[0]);
        VersionJsonRef ref = new VersionJsonRef(ID, server.uri("/version"), new Sha1("1".repeat(40)));
        DownloadException failure = assertThrows(DownloadException.class, () -> service.ensure(ref));
        assertTrue(failure.getMessage().contains("HTTP 500"), failure.getMessage());
        assertEquals(List.of(), filesUnderJars());
    }

    @Test
    void versionJsonWithoutAServerDownloadStopsBeforeAnyJarRequest() throws IOException {
        byte[] versionJson = "{\"id\": \"99.1-rc-1\", \"downloads\": {\"client\": {}}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        server.serve("/version", 200, versionJson);
        VersionJsonRef ref = new VersionJsonRef(ID, server.uri("/version"), new Sha1(FetchFixtures.sha1Hex(versionJson)));
        VersionJsonFormatException failure = assertThrows(VersionJsonFormatException.class, () -> service.ensure(ref));
        assertTrue(failure.getMessage().contains("99.1-rc-1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("downloads.server"), failure.getMessage());
        assertEquals(List.of(), filesUnderJars());
    }

    @Test
    void interruptedThreadStopsWithoutLeavingAFileAndKeepsItsFlag() throws IOException {
        VersionJsonRef ref = publishValidJar();
        Thread.currentThread().interrupt();
        try {
            assertThrows(DownloadException.class, () -> service.ensure(ref));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertEquals(List.of(), filesUnderJars());
    }

    @Test
    void interruptionDuringTheJarTransferLeavesNoFileAndKeepsTheFlag() throws IOException {
        byte[] versionJson = FetchFixtures.versionJson(ID.value(), FetchFixtures.sha1Hex(JAR), JAR.length, server.uri("/stall").toString());
        server.serve("/version", 200, versionJson);
        server.serveThenStall("/stall", new byte[1000]);
        VersionJsonRef ref = new VersionJsonRef(ID, server.uri("/version"), new Sha1(FetchFixtures.sha1Hex(versionJson)));
        Thread main = Thread.currentThread();
        Thread interrupter = new Thread(() -> {
            try {
                Thread.sleep(700);
            } catch (InterruptedException exception) {
                return;
            }
            main.interrupt();
        });
        interrupter.start();
        try {
            assertThrows(DownloadException.class, () -> service.ensure(ref));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertEquals(List.of(), filesUnderJars());
    }

    @Test
    void stalledTransferEndsWithATimeoutAndLeavesNoFile() throws IOException {
        byte[] versionJson = FetchFixtures.versionJson(ID.value(), FetchFixtures.sha1Hex(JAR), JAR.length, server.uri("/stall").toString());
        server.serve("/version", 200, versionJson);
        server.serveThenStall("/stall", new byte[1000]);
        ServerJarService impatient = new ServerJarService(
                new HttpDownloadClient(http, Duration.ofSeconds(5), Duration.ofSeconds(2)),
                new JarStore(jarsRoot),
                new VersionJsonReader(new JsonTextReader()),
                new Sha1Hasher());
        VersionJsonRef ref = new VersionJsonRef(ID, server.uri("/version"), new Sha1(FetchFixtures.sha1Hex(versionJson)));
        DownloadException failure = assertThrows(DownloadException.class, () -> impatient.ensure(ref));
        assertTrue(failure.getMessage().contains("99.1-rc-1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("did not finish within 2 seconds"), failure.getMessage());
        assertEquals(List.of(), filesUnderJars());
    }
}
