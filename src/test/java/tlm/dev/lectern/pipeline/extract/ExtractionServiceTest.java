package tlm.dev.lectern.pipeline.extract;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlm.dev.lectern.pipeline.fixtures.FakeLauncher;
import tlm.dev.lectern.pipeline.fixtures.JarFixtures;
import tlm.dev.lectern.pipeline.fixtures.SteppingClock;
import tlm.dev.lectern.pipeline.model.VersionId;

class ExtractionServiceTest {

    private static final VersionId ID = new VersionId("99.1");
    private static final Path JAVA = Path.of("jdk", "bin", "java");
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    @TempDir
    Path workbench;

    @TempDir
    Path scratch;

    private Path generatedRoot;
    private Path jar;
    private ExtractionSettings settings;

    @BeforeEach
    void setUp() throws IOException {
        generatedRoot = workbench.resolve("generated");
        Files.createDirectories(workbench.resolve("jars").resolve("99.1"));
        jar = workbench.resolve("jars").resolve("99.1").resolve("server.jar");
        settings = new ExtractionSettings(generatedRoot, scratch, JAVA, TIMEOUT);
    }

    private ExtractionService service(FakeLauncher launcher) {
        return new ExtractionService(launcher, new JarEraDetector(), settings, new SteppingClock(Instant.parse("2090-01-01T00:00:00Z"), Duration.ofMillis(6100)));
    }

    private List<String> scratchContent() throws IOException {
        try (Stream<Path> children = Files.list(scratch)) {
            return children.map(path -> path.getFileName().toString()).toList();
        }
    }

    @Test
    void bundlerJarIsRunWithTheBundlerCommandAndTheResultIsSummarized() throws IOException {
        JarFixtures.bundlerJar(jar);
        FakeLauncher launcher = FakeLauncher.writingFiles("reports/blocks.json", "data/minecraft/recipe/a.json", ".cache/abc");
        ExtractionResult result = service(launcher).extract(ID, jar);
        FakeLauncher.Call call = launcher.calls().get(0);
        assertEquals(
                GeneratorCommandPlanner.plan(new JarEra.Bundler(), JAVA, jar.toAbsolutePath(), generatedRoot.resolve("99.1").toAbsolutePath()),
                call.command());
        assertEquals(TIMEOUT, call.timeout());
        assertEquals(generatedRoot.resolve("99.1"), result.output());
        long reportsAndData = "reports/blocks.json".length() + "data/minecraft/recipe/a.json".length();
        assertEquals(new TreeSummary(3, reportsAndData + ".cache/abc".length(), 2, reportsAndData), result.summary());
        assertEquals(Duration.ofMillis(6100), result.duration());
        assertTrue(Files.isRegularFile(result.output().resolve("reports/blocks.json")));
    }

    @Test
    void simpleJarIsRunOnTheClassPath() {
        JarFixtures.simpleJar(jar);
        FakeLauncher launcher = FakeLauncher.writingFiles("reports/blocks.json");
        service(launcher).extract(ID, jar);
        List<String> command = launcher.calls().get(0).command();
        assertEquals(List.of("-cp", jar.toAbsolutePath().toString(), "net.minecraft.data.Main"), command.subList(1, 4));
    }

    @Test
    void workFolderIsNewOutsideTheWorkbenchAndDeletedAfterTheRun() throws IOException {
        JarFixtures.bundlerJar(jar);
        FakeLauncher launcher = FakeLauncher.writingFiles("reports/blocks.json");
        service(launcher).extract(ID, jar);
        FakeLauncher.Call call = launcher.calls().get(0);
        assertTrue(call.workingDirectoryExisted());
        assertTrue(call.workingDirectory().startsWith(scratch));
        assertFalse(call.workingDirectory().startsWith(workbench));
        assertFalse(Files.exists(call.workingDirectory()));
        assertEquals(List.of(), scratchContent());
    }

    @Test
    void everyRunGetsItsOwnWorkFolder() {
        JarFixtures.bundlerJar(jar);
        FakeLauncher launcher = new FakeLauncher(call -> {
            FakeLauncher.write(FakeLauncher.outputOf(call.command()).resolve("reports/x.json"), 1);
            return new ProcessOutcome.Exited(0, "", "");
        });
        ExtractionService service = service(launcher);
        service.extract(ID, jar);
        service.extract(new VersionId("99.2"), jar);
        assertTrue(!launcher.calls().get(0).workingDirectory().equals(launcher.calls().get(1).workingDirectory()));
    }

    @Test
    void existingOutputFolderIsRefusedAndNothingInItChanges() throws IOException {
        JarFixtures.bundlerJar(jar);
        Path existing = generatedRoot.resolve("99.1").resolve("reports/blocks.json");
        FakeLauncher.write(existing, 11);
        FakeLauncher launcher = FakeLauncher.writingFiles("reports/other.json");
        OutputExistsException failure = assertThrows(OutputExistsException.class, () -> service(launcher).extract(ID, jar));
        assertTrue(failure.getMessage().contains("99.1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("already exists"), failure.getMessage());
        assertEquals(List.of(), launcher.calls());
        assertArrayEquals(new byte[11], Files.readAllBytes(existing));
        assertFalse(Files.exists(generatedRoot.resolve("99.1").resolve("reports/other.json")));
        assertEquals(List.of(), scratchContent());
    }

    @Test
    void existingEmptyOutputFolderIsRefusedToo() throws IOException {
        JarFixtures.bundlerJar(jar);
        Files.createDirectories(generatedRoot.resolve("99.1"));
        FakeLauncher launcher = FakeLauncher.writingFiles("reports/other.json");
        assertThrows(OutputExistsException.class, () -> service(launcher).extract(ID, jar));
        assertEquals(List.of(), launcher.calls());
    }

    @Test
    void nonZeroExitIsAFailureWithTheEndOfStderrAndTheWorkFolderIsDeleted() throws IOException {
        JarFixtures.bundlerJar(jar);
        FakeLauncher launcher = new FakeLauncher(call -> {
            FakeLauncher.write(FakeLauncher.outputOf(call.command()).resolve("reports/partial.json"), 3);
            return new ProcessOutcome.Exited(3, "generator log line", "WARNING: sun.misc.Unsafe\nException in thread main: invented boom");
        });
        GeneratorFailureException failure = assertThrows(GeneratorFailureException.class, () -> service(launcher).extract(ID, jar));
        assertTrue(failure.getMessage().contains("Version 99.1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("exited with code 3"), failure.getMessage());
        assertTrue(failure.getMessage().contains("invented boom"), failure.getMessage());
        assertTrue(failure.getMessage().contains("generator log line"), failure.getMessage());
        assertTrue(failure.getMessage().contains("left as it is"), failure.getMessage());
        assertTrue(Files.exists(generatedRoot.resolve("99.1").resolve("reports/partial.json")));
        assertEquals(List.of(), scratchContent());
    }

    @Test
    void deprecationWarningsOnStderrDoNotMakeASuccessfulRunFail() {
        JarFixtures.bundlerJar(jar);
        FakeLauncher launcher = new FakeLauncher(call -> {
            FakeLauncher.write(FakeLauncher.outputOf(call.command()).resolve("reports/blocks.json"), 2);
            return new ProcessOutcome.Exited(0, "", "WARNING: A terminally deprecated method in sun.misc.Unsafe has been called");
        });
        assertEquals(1, service(launcher).extract(ID, jar).summary().files());
    }

    @Test
    void timeoutIsAFailureNamingTheLimitAndTheWorkFolderIsDeleted() throws IOException {
        JarFixtures.bundlerJar(jar);
        FakeLauncher launcher = new FakeLauncher(call -> new ProcessOutcome.TimedOut(TIMEOUT, "slow log", "still running"));
        GeneratorFailureException failure = assertThrows(GeneratorFailureException.class, () -> service(launcher).extract(ID, jar));
        assertTrue(failure.getMessage().contains("did not finish within 600 seconds"), failure.getMessage());
        assertTrue(failure.getMessage().contains("still running"), failure.getMessage());
        assertEquals(List.of(), scratchContent());
    }

    @Test
    void launcherThatCannotStartTheProcessIsAFailureNamingTheVersion() throws IOException {
        JarFixtures.bundlerJar(jar);
        FakeLauncher launcher = new FakeLauncher(call -> {
            throw new ProcessLaunchException("Could not start jdk/bin/java: not found", new IOException("not found"));
        });
        GeneratorFailureException failure = assertThrows(GeneratorFailureException.class, () -> service(launcher).extract(ID, jar));
        assertTrue(failure.getMessage().contains("Version 99.1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("could not run the data generator"), failure.getMessage());
        assertEquals(List.of(), scratchContent());
    }

    @Test
    void successfulExitWithoutAnOutputFolderIsAFailure() {
        JarFixtures.bundlerJar(jar);
        FakeLauncher launcher = new FakeLauncher(call -> new ProcessOutcome.Exited(0, "", ""));
        GeneratorFailureException failure = assertThrows(GeneratorFailureException.class, () -> service(launcher).extract(ID, jar));
        assertTrue(failure.getMessage().contains("wrote no folder"), failure.getMessage());
    }

    @Test
    void unsupportedJarStopsBeforeAnyProcessOrWorkFolder() throws IOException {
        JarFixtures.jarWithoutManifest(jar);
        FakeLauncher launcher = FakeLauncher.writingFiles("reports/blocks.json");
        assertThrows(UnsupportedJarException.class, () -> service(launcher).extract(ID, jar));
        assertEquals(List.of(), launcher.calls());
        assertEquals(List.of(), scratchContent());
        assertFalse(Files.exists(generatedRoot.resolve("99.1")));
    }

    @Test
    void generatedRootIsCreatedWhenMissing() {
        JarFixtures.bundlerJar(jar);
        assertFalse(Files.exists(generatedRoot));
        service(FakeLauncher.writingFiles("reports/blocks.json")).extract(ID, jar);
        assertTrue(Files.isDirectory(generatedRoot));
    }

    @Test
    void settingsRefuseANonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new ExtractionSettings(generatedRoot, scratch, JAVA, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionSettings(generatedRoot, scratch, JAVA, Duration.ofSeconds(-1)));
    }
}
