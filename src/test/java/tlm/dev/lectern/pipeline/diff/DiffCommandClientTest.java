package tlm.dev.lectern.pipeline.diff;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlm.dev.lectern.pipeline.fixtures.DiffFixtures;
import tlm.dev.lectern.pipeline.fixtures.FakeLauncher;
import tlm.dev.lectern.pipeline.json.CanonicalJsonWriter;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.model.VersionKind;
import tlm.dev.lectern.pipeline.process.ProcessLaunchException;
import tlm.dev.lectern.pipeline.process.ProcessOutcome;

class DiffCommandClientTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    @TempDir
    Path temporary;

    private Path script;
    private Path scratch;
    private DiffSide from;
    private DiffSide to;

    @BeforeEach
    void setUp() throws IOException {
        script = temporary.resolve("bin").resolve("LecternDiff");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "placeholder");
        scratch = temporary.resolve("scratch");
        from = new DiffSide(new VersionId("99.1-rc-1"), new VersionKind.ReleaseCandidate(), Files.createDirectories(temporary.resolve("normalized/99.1-rc-1")));
        to = new DiffSide(new VersionId("99.1"), new VersionKind.Release(), Files.createDirectories(temporary.resolve("normalized/99.1")));
    }

    private DiffCommandClient client(Function<FakeLauncher.Call, ProcessOutcome> behaviour, FakeLauncher[] holder) {
        FakeLauncher launcher = new FakeLauncher(behaviour);
        holder[0] = launcher;
        return new DiffCommandClient(launcher, new DiffCommand(script, TIMEOUT),
                new CanonicalOutputVerifier(new JsonTextReader(), new CanonicalJsonWriter()), scratch);
    }

    private static String canonical() {
        return DiffFixtures.canonicalDiff("99.1-rc-1", "release-candidate", "99.1", "release", 2);
    }

    @Test
    void commandLineCarriesTheTwoFoldersTheIdsAndTheKinds() {
        FakeLauncher[] holder = new FakeLauncher[1];
        client(call -> new ProcessOutcome.Exited(0, canonical(), ""), holder).diff(from, to);
        FakeLauncher.Call call = holder[0].calls().get(0);
        assertEquals(List.of(script.toString(), "diff", from.folder().toString(), to.folder().toString(),
                "--from", "99.1-rc-1", "--from-kind", "release-candidate", "--to", "99.1", "--to-kind", "release"), call.command());
        assertEquals(TIMEOUT, call.timeout());
    }

    @Test
    void canonicalOutputIsReturnedAsBytes() {
        FakeLauncher[] holder = new FakeLauncher[1];
        byte[] bytes = client(call -> new ProcessOutcome.Exited(0, canonical(), "warning on stderr is not an error"), holder).diff(from, to);
        assertArrayEquals(canonical().getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    void workFolderIsNewOutsideTheNormalizedFoldersAndDeletedAfterwards() throws IOException {
        FakeLauncher[] holder = new FakeLauncher[1];
        client(call -> new ProcessOutcome.Exited(0, canonical(), ""), holder).diff(from, to);
        FakeLauncher.Call call = holder[0].calls().get(0);
        assertTrue(call.workingDirectoryExisted());
        assertTrue(call.workingDirectory().startsWith(scratch));
        assertFalse(Files.exists(call.workingDirectory()));
        try (var children = Files.list(scratch)) {
            assertEquals(0, children.count());
        }
    }

    @Test
    void nonZeroExitStopsWithTheEndOfStderrAndNamesBothVersions() {
        FakeLauncher[] holder = new FakeLauncher[1];
        DiffCommandClient client = client(call -> new ProcessOutcome.Exited(1, "", "line one\nerror: Version 99.1, reports/blocks.json: invented"), holder);
        DiffException failure = assertThrows(DiffException.class, () -> client.diff(from, to));
        assertTrue(failure.getMessage().contains("Version 99.1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("exited with code 1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("99.1-rc-1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("error: Version 99.1, reports/blocks.json: invented"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Nothing was written"), failure.getMessage());
    }

    @Test
    void usageExitCodeIsAnErrorToo() {
        FakeLauncher[] holder = new FakeLauncher[1];
        DiffCommandClient client = client(call -> new ProcessOutcome.Exited(2, "", "error: unknown option\nusage: diff"), holder);
        DiffException failure = assertThrows(DiffException.class, () -> client.diff(from, to));
        assertTrue(failure.getMessage().contains("exited with code 2"), failure.getMessage());
    }

    @Test
    void zeroExitWithOutputThatIsNotCanonicalStopsAndNamesTheVersion() {
        FakeLauncher[] holder = new FakeLauncher[1];
        DiffCommandClient client = client(call -> new ProcessOutcome.Exited(0, canonical().replace("\n", "\r\n"), ""), holder);
        DiffException failure = assertThrows(DiffException.class, () -> client.diff(from, to));
        assertTrue(failure.getMessage().contains("Version 99.1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("not in canonical JSON form"), failure.getMessage());
    }

    @Test
    void timeoutStopsWithTheLimitInTheMessage() {
        FakeLauncher[] holder = new FakeLauncher[1];
        DiffCommandClient client = client(call -> new ProcessOutcome.TimedOut(TIMEOUT, "", ""), holder);
        DiffException failure = assertThrows(DiffException.class, () -> client.diff(from, to));
        assertTrue(failure.getMessage().contains("did not finish within 90 seconds"), failure.getMessage());
    }

    @Test
    void scriptThatCannotBeStartedKeepsTheCause() {
        FakeLauncher[] holder = new FakeLauncher[1];
        DiffCommandClient client = client(call -> {
            throw new ProcessLaunchException("Could not start the script: not executable", new IOException("not executable"));
        }, holder);
        DiffException failure = assertThrows(DiffException.class, () -> client.diff(from, to));
        assertTrue(failure.getMessage().contains("could not run the diff command"), failure.getMessage());
        assertTrue(failure.getCause() instanceof ProcessLaunchException);
    }

    @Test
    void missingScriptIsReportedWithTheWayToBuildItAndNothingIsLaunched() throws IOException {
        Files.delete(script);
        FakeLauncher[] holder = new FakeLauncher[1];
        DiffCommandClient client = client(call -> new ProcessOutcome.Exited(0, canonical(), ""), holder);
        DiffException failure = assertThrows(DiffException.class, () -> client.diff(from, to));
        assertTrue(failure.getMessage().contains("does not exist"), failure.getMessage());
        assertTrue(failure.getMessage().contains("installDist"), failure.getMessage());
        assertEquals(0, holder[0].calls().size());
        assertThrows(DiffException.class, client::requireInstalled);
    }

    @Test
    void missingNormalizedFolderIsReportedBeforeAnythingIsLaunched() throws IOException {
        Files.delete(from.folder());
        FakeLauncher[] holder = new FakeLauncher[1];
        DiffCommandClient client = client(call -> new ProcessOutcome.Exited(0, canonical(), ""), holder);
        DiffException failure = assertThrows(DiffException.class, () -> client.diff(from, to));
        assertTrue(failure.getMessage().contains("99.1-rc-1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("normalize it first"), failure.getMessage());
        assertEquals(0, holder[0].calls().size());
    }

    @Test
    void installedScriptPassesTheCheck() {
        FakeLauncher[] holder = new FakeLauncher[1];
        client(call -> new ProcessOutcome.Exited(0, canonical(), ""), holder).requireInstalled();
    }
}
