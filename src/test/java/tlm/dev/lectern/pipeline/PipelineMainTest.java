package tlm.dev.lectern.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlm.dev.lectern.pipeline.extract.ProcessOutcome;
import tlm.dev.lectern.pipeline.fixtures.FakeLauncher;
import tlm.dev.lectern.pipeline.fixtures.FetchFixtures;
import tlm.dev.lectern.pipeline.fixtures.JarFixtures;
import tlm.dev.lectern.pipeline.fixtures.LocalFileServer;
import tlm.dev.lectern.pipeline.fixtures.ManifestFixtures;
import tlm.dev.lectern.pipeline.fixtures.SteppingClock;

class PipelineMainTest {

    @TempDir
    Path base;

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private LocalFileServer server;
    private Path workbench;
    private Environment environment;
    private byte[] bundlerJar;

    @BeforeEach
    void setUp() throws IOException {
        server = new LocalFileServer();
        workbench = base.resolve("LecternWorkbench");
        Files.createDirectories(workbench);
        environment = new Environment(base.resolve("LecternPipeline"), base.resolve("jdk"), base.resolve("tmp"));
        bundlerJar = Files.readAllBytes(JarFixtures.bundlerJar(base.resolve("model-bundler.jar")));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private PipelineMain main(FakeLauncher launcher) {
        Clock clock = new SteppingClock(Instant.parse("2090-01-01T00:00:00Z"), Duration.ofMillis(6100));
        return new PipelineMain(
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8),
                environment, launcher, clock);
    }

    private int run(FakeLauncher launcher, String... args) {
        return main(launcher).run(args);
    }

    private int run(String... args) {
        return run(FakeLauncher.writingFiles("reports/blocks.json"), args);
    }

    private String out() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    private String releaseEntry(String id, byte[] jarOnServer, String sha1Declared, int hoursAfterOrigin) {
        byte[] versionJson = FetchFixtures.versionJson(id, sha1Declared, bundlerJar.length, server.uri("/jar/" + id).toString());
        server.serve("/jar/" + id, 200, jarOnServer);
        server.serve("/version/" + id, 200, versionJson);
        return ManifestFixtures.entryJson(id, "release", ManifestFixtures.hoursAfterOrigin(hoursAfterOrigin),
                server.uri("/version/" + id).toString(), FetchFixtures.sha1Hex(versionJson));
    }

    private void publishManifest(String... entries) {
        server.serve("/manifest", 200, ManifestFixtures.manifestJson(entries).getBytes(StandardCharsets.UTF_8));
    }

    private String[] arguments(String... leading) {
        return Stream.concat(Stream.of(leading), Stream.of("--workbench=" + workbench, "--manifest-url=" + server.uri("/manifest"))).toArray(String[]::new);
    }

    private List<String> filesIn(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            return walk.filter(Files::isRegularFile).map(path -> folder.relativize(path).toString().replace('\\', '/')).sorted().toList();
        }
    }

    @Test
    void listPrintsTheRecentVersionsWithKindReleaseTimeAndDiffBase() {
        List<String> entries = List.of(
                ManifestFixtures.entryJson("99.2-snapshot-1", "snapshot", ManifestFixtures.hoursAfterOrigin(300)),
                ManifestFixtures.entryJson("26w14a", "snapshot", ManifestFixtures.hoursAfterOrigin(250)),
                ManifestFixtures.entryJson("99.1", "release", ManifestFixtures.hoursAfterOrigin(200)),
                ManifestFixtures.entryJson("99.1-rc-1", "snapshot", ManifestFixtures.hoursAfterOrigin(100)),
                ManifestFixtures.entryJson("1.14 Pre-Release 1", "snapshot", ManifestFixtures.hoursAfterOrigin(50)),
                ManifestFixtures.entryJson("98.9", "release", ManifestFixtures.hoursAfterOrigin(10)));
        publishManifest(entries.toArray(new String[0]));
        int exit = run("list", "--manifest-url=" + server.uri("/manifest"));
        assertEquals("", err());
        assertEquals(0, exit);
        assertEquals("""
                ID               KIND               RELEASED              BASE       RELEASE BASE
                99.2-snapshot-1  snapshot           2090-01-13T12:00:00Z  99.1       -
                99.1             release            2090-01-09T08:00:00Z  99.1-rc-1  98.9
                99.1-rc-1        release-candidate  2090-01-05T04:00:00Z  98.9       -
                98.9             release            2090-01-01T10:00:00Z  -          -
                """, out());
    }

    @Test
    void noArgumentPrintsTheUsageAndExitsWithTwo() {
        int exit = run();
        assertEquals(2, exit);
        assertEquals("", out());
        assertEquals(PipelineConfigReader.USAGE + System.lineSeparator(), err());
    }

    @Test
    void unknownCommandExitsWithTwo() {
        assertEquals(2, run("download"));
        assertTrue(err().contains("Unknown command 'download'"), err());
    }

    @Test
    void listFetchFailureExitsWithOneAndNamesTheStatus() {
        server.serve("/manifest", 503, new byte[0]);
        assertEquals(1, run("list", "--manifest-url=" + server.uri("/manifest")));
        assertEquals("", out());
        assertTrue(err().startsWith("lectern-pipeline: "), err());
        assertTrue(err().contains("HTTP 503"), err());
    }

    @Test
    void malformedManifestExitsWithOneAndPrintsNothingOnStandardOutput() {
        server.serve("/manifest", 200, "{\"versions\": [{\"id\": \"99.1\"}]}".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, run("list", "--manifest-url=" + server.uri("/manifest")));
        assertEquals("", out());
        assertTrue(err().contains("entry 0"), err());
    }

    @Test
    void fetchDownloadsTheVerifiedJarIntoTheWorkbench() throws IOException {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        int exit = run(arguments("fetch", "99.1"));
        assertEquals("", err());
        assertEquals(0, exit);
        Path jar = workbench.resolve("jars").resolve("99.1").resolve("server.jar");
        assertArrayEquals(bundlerJar, Files.readAllBytes(jar));
        assertTrue(out().startsWith("fetched 99.1 (downloaded)\n"), out());
        assertTrue(out().contains("sha1   " + FetchFixtures.sha1Hex(bundlerJar)), out());
        assertEquals(List.of("99.1/server.jar"), filesIn(workbench.resolve("jars")));
    }

    @Test
    void fetchOfAJarAlreadyThereDoesNotDownloadItAgain() {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        assertEquals(0, run(arguments("fetch", "99.1")));
        outBytes.reset();
        assertEquals(0, run(arguments("fetch", "99.1")));
        assertTrue(out().startsWith("fetched 99.1 (already present)\n"), out());
        assertEquals(1, server.hits("/jar/99.1"));
    }

    @Test
    void fetchWithAWrongJarHashExitsWithOneNamingTheVersionAndBothHashes() throws IOException {
        byte[] served = FetchFixtures.inventedBytes(11, bundlerJar.length);
        publishManifest(releaseEntry("99.1", served, FetchFixtures.sha1Hex(bundlerJar), 10));
        assertEquals(1, run(arguments("fetch", "99.1")));
        assertEquals("", out());
        assertTrue(err().contains("Version 99.1"), err());
        assertTrue(err().contains("Expected " + FetchFixtures.sha1Hex(bundlerJar)), err());
        assertTrue(err().contains("received " + FetchFixtures.sha1Hex(served)), err());
        assertEquals(List.of(), filesIn(workbench.resolve("jars")));
    }

    @Test
    void fetchRefusesAnExcludedVersionAndSaysSo() throws IOException {
        publishManifest(ManifestFixtures.entryJson("26w14a", "snapshot", ManifestFixtures.hoursAfterOrigin(5)));
        assertEquals(1, run(arguments("fetch", "26w14a")));
        assertTrue(err().contains("exclusion list"), err());
        assertEquals(List.of(), filesIn(workbench.resolve("jars")));
    }

    @Test
    void fetchRefusesAVersionWithoutANormalShapeAndSaysSo() throws IOException {
        publishManifest(ManifestFixtures.entryJson("1.14 Pre-Release 1", "snapshot", ManifestFixtures.hoursAfterOrigin(5)));
        assertEquals(1, run(arguments("fetch", "1.14 Pre-Release 1")));
        assertTrue(err().contains("not shaped like"), err());
        assertEquals(List.of(), filesIn(workbench.resolve("jars")));
    }

    @Test
    void fetchRefusesAVersionAbsentFromTheManifestAndSaysSo() {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        assertEquals(1, run(arguments("fetch", "98.9")));
        assertTrue(err().contains("not in the Mojang manifest"), err());
        assertEquals(0, server.hits("/jar/99.1"));
    }

    @Test
    void fetchWithoutAWorkbenchFolderExitsWithOneAndSaysHowToFixIt() {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        int exit = run("fetch", "99.1", "--workbench=" + base.resolve("absent"), "--manifest-url=" + server.uri("/manifest"));
        assertEquals(1, exit);
        assertTrue(err().contains("does not exist"), err());
        assertTrue(err().contains("--workbench"), err());
        assertEquals(0, server.hits("/manifest"));
        assertFalse(Files.exists(base.resolve("absent")));
    }

    @Test
    void defaultWorkbenchIsTheFolderNextToTheCurrentDirectory() throws IOException {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        assertEquals(0, run("fetch", "99.1", "--manifest-url=" + server.uri("/manifest")));
        assertTrue(Files.isRegularFile(base.resolve("LecternWorkbench").resolve("jars").resolve("99.1").resolve("server.jar")));
    }

    @Test
    void extractFetchesTheJarThenRunsTheGeneratorAndReportsTheResult() throws IOException {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        FakeLauncher launcher = FakeLauncher.writingFiles("reports/blocks.json", "data/minecraft/recipe/a.json", ".cache/abc");
        int exit = run(launcher, arguments("extract", "99.1"));
        assertEquals("", err());
        assertEquals(0, exit);
        assertTrue(out().contains("fetched 99.1 (downloaded)"), out());
        assertTrue(out().contains("extracted 99.1"), out());
        assertTrue(out().contains("files     3 (2 without .cache)"), out());
        assertTrue(out().contains("duration  6.1 s"), out());
        assertEquals(1, launcher.calls().size());
        assertTrue(launcher.calls().get(0).command().contains("-DbundlerMainClass=net.minecraft.data.Main"));
        assertEquals(List.of(".cache/abc", "data/minecraft/recipe/a.json", "reports/blocks.json"), filesIn(workbench.resolve("generated").resolve("99.1")));
    }

    @Test
    void extractUsesTheJarAlreadyInTheWorkbenchWithoutDownloadingIt() {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        assertEquals(0, run(arguments("fetch", "99.1")));
        outBytes.reset();
        assertEquals(0, run(arguments("extract", "99.1")));
        assertTrue(out().contains("fetched 99.1 (already present)"), out());
        assertEquals(1, server.hits("/jar/99.1"));
    }

    @Test
    void extractRefusesAnExistingOutputFolderBeforeAnyNetworkAccess() throws IOException {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        Path existing = workbench.resolve("generated").resolve("99.1").resolve("reports").resolve("blocks.json");
        FakeLauncher.write(existing, 9);
        FakeLauncher launcher = FakeLauncher.writingFiles("reports/other.json");
        assertEquals(1, run(launcher, arguments("extract", "99.1")));
        assertTrue(err().contains("already exists"), err());
        assertEquals(0, server.hits("/manifest"));
        assertEquals(0, server.hits("/jar/99.1"));
        assertEquals(List.of(), launcher.calls());
        assertEquals(List.of("reports/blocks.json"), filesIn(workbench.resolve("generated").resolve("99.1")));
        assertArrayEquals(new byte[9], Files.readAllBytes(existing));
    }

    @Test
    void extractRefusesAnExcludedVersionBeforeDownloadingAnything() throws IOException {
        publishManifest(ManifestFixtures.entryJson("26w14a", "snapshot", ManifestFixtures.hoursAfterOrigin(5)));
        FakeLauncher launcher = FakeLauncher.writingFiles("reports/blocks.json");
        assertEquals(1, run(launcher, arguments("extract", "26w14a")));
        assertTrue(err().contains("exclusion list"), err());
        assertEquals(List.of(), launcher.calls());
        assertEquals(List.of(), filesIn(workbench));
    }

    @Test
    void generatorFailureExitsWithOneWithTheEndOfStderr() {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        FakeLauncher launcher = new FakeLauncher(call -> new ProcessOutcome.Exited(1, "", "Exception in thread main: invented boom"));
        assertEquals(1, run(launcher, arguments("extract", "99.1")));
        assertTrue(err().startsWith("lectern-pipeline: Version 99.1"), err());
        assertTrue(err().contains("invented boom"), err());
        assertTrue(out().contains("fetched 99.1 (downloaded)"), out());
        assertFalse(out().contains("extracted"), out());
    }
}
