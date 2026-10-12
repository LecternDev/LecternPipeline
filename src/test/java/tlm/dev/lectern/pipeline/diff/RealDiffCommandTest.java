package tlm.dev.lectern.pipeline.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tlm.dev.lectern.pipeline.fixtures.RealDiffScript;
import tlm.dev.lectern.pipeline.json.CanonicalJsonWriter;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.json.JsonValue;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.model.VersionKind;
import tlm.dev.lectern.pipeline.process.JdkProcessLauncher;

class RealDiffCommandTest {

    private static final Path WORKBENCH = Path.of("").toAbsolutePath().resolveSibling("LecternWorkbench");
    private static final Map<String, VersionKind> KINDS = Map.of(
            "26.3-rc-3", new VersionKind.ReleaseCandidate(),
            "26.3", new VersionKind.Release(),
            "26.4-snapshot-3", new VersionKind.Snapshot(),
            "1.21.11", new VersionKind.Release(),
            "1.20.4", new VersionKind.Release(),
            "1.14.4", new VersionKind.Release());

    @TempDir
    Path scratch;

    private DiffSide side(String id) {
        return new DiffSide(new VersionId(id), KINDS.get(id), WORKBENCH.resolve("normalized").resolve(id));
    }

    private DiffCommandClient client(Path script) {
        return new DiffCommandClient(new JdkProcessLauncher(), new DiffCommand(script, Duration.ofMinutes(5)),
                new CanonicalOutputVerifier(new JsonTextReader(), new CanonicalJsonWriter()), scratch);
    }

    private static int changeCount(byte[] diff) {
        JsonValue.ObjectValue root = (JsonValue.ObjectValue) new JsonTextReader().read(new String(diff, StandardCharsets.UTF_8));
        return ((JsonValue.ArrayValue) root.get("changes").orElseThrow()).elements().size();
    }

    @ParameterizedTest
    @CsvSource({"26.3,26.4-snapshot-3", "1.14.4,1.20.4", "1.20.4,1.21.11", "1.21.11,26.3"})
    void realDiffOfTwoDifferentVersionsIsCanonicalAndNotEmpty(String from, String to) {
        Path script = RealDiffScript.require();
        RealDiffScript.requireNormalized(WORKBENCH, List.of(from, to));
        byte[] diff = client(script).diff(side(from), side(to));
        assertTrue(changeCount(diff) > 0);
        assertTrue(new String(diff, StandardCharsets.UTF_8).endsWith("\n"));
    }

    @Test
    void releaseCandidateAndItsReleaseHaveTheSameRawDataSoTheirDiffIsEmpty() {
        Path script = RealDiffScript.require();
        RealDiffScript.requireNormalized(WORKBENCH, List.of("26.3-rc-3", "26.3"));
        byte[] diff = client(script).diff(side("26.3-rc-3"), side("26.3"));
        assertEquals(0, changeCount(diff));
        assertFalse(new String(diff, StandardCharsets.UTF_8).isBlank());
    }

    @Test
    void sameDiffTwiceGivesTheSameBytes() {
        Path script = RealDiffScript.require();
        RealDiffScript.requireNormalized(WORKBENCH, List.of("26.3", "26.4-snapshot-3"));
        DiffCommandClient client = client(script);
        assertEquals(new String(client.diff(side("26.3"), side("26.4-snapshot-3")), StandardCharsets.UTF_8),
                new String(client.diff(side("26.3"), side("26.4-snapshot-3")), StandardCharsets.UTF_8));
    }
}
