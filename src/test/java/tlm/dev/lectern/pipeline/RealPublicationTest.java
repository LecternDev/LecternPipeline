package tlm.dev.lectern.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlm.dev.lectern.pipeline.diff.CanonicalOutputVerifier;
import tlm.dev.lectern.pipeline.diff.DiffCommand;
import tlm.dev.lectern.pipeline.diff.DiffCommandClient;
import tlm.dev.lectern.pipeline.diff.DiffSide;
import tlm.dev.lectern.pipeline.fixtures.GitFixtures;
import tlm.dev.lectern.pipeline.fixtures.RealDiffScript;
import tlm.dev.lectern.pipeline.json.CanonicalJsonWriter;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.normalize.DataAllowlist;
import tlm.dev.lectern.pipeline.normalize.TreeScanner;
import tlm.dev.lectern.pipeline.normalize.VersionMeta;
import tlm.dev.lectern.pipeline.normalize.VersionMetaReader;
import tlm.dev.lectern.pipeline.process.JdkProcessLauncher;
import tlm.dev.lectern.pipeline.publish.DataRepositoryRules;
import tlm.dev.lectern.pipeline.publish.GitClient;
import tlm.dev.lectern.pipeline.publish.PublicationRequest;
import tlm.dev.lectern.pipeline.publish.PublicationResult;
import tlm.dev.lectern.pipeline.publish.PublicationService;
import tlm.dev.lectern.pipeline.publish.RepositoryGuard;
import tlm.dev.lectern.pipeline.publish.TreeUpdater;

class RealPublicationTest {

    private static final Path WORKBENCH = Path.of("").toAbsolutePath().resolveSibling("LecternWorkbench");
    private static final List<String> IDS = List.of("26.3-rc-3", "26.3", "26.4-snapshot-3");

    @TempDir
    Path temporary;

    private final TreeScanner scanner = new TreeScanner();
    private Path repository;
    private PublicationService publication;
    private DiffCommandClient diff;

    private void prepare() {
        assumeTrue(GitFixtures.gitAvailable(), "git is not available on this machine");
        Path script = RealDiffScript.require();
        RealDiffScript.requireNormalized(WORKBENCH, IDS);
        repository = GitFixtures.initRepository(temporary.resolve("data-repository"));
        GitClient git = new GitClient(new JdkProcessLauncher(), Path.of("git"), Duration.ofMinutes(5));
        publication = new PublicationService(git, new RepositoryGuard(git), new TreeUpdater(), temporary.resolve("scratch"));
        diff = new DiffCommandClient(new JdkProcessLauncher(), new DiffCommand(script, Duration.ofMinutes(5)),
                new CanonicalOutputVerifier(new JsonTextReader(), new CanonicalJsonWriter()), temporary.resolve("scratch"));
    }

    private Path normalized(String id) {
        return WORKBENCH.resolve("normalized").resolve(id);
    }

    private VersionMeta meta(String id) {
        return new VersionMetaReader(new JsonTextReader()).read(new VersionId(id), normalized(id).resolve("meta.json"));
    }

    private PublicationResult publish(String id, Map<String, byte[]> diffs) {
        VersionId versionId = new VersionId(id);
        List<String> files = scanner.scan(versionId, normalized(id), DataAllowlist.Layout.NORMALIZED);
        return publication.publish(new PublicationRequest(versionId, normalized(id), files, diffs, repository));
    }

    private byte[] diffBetween(String from, String to) {
        return diff.diff(new DiffSide(new VersionId(from), meta(from).kind(), normalized(from)), new DiffSide(new VersionId(to), meta(to).kind(), normalized(to)));
    }

    private Set<String> committed(String revision) {
        return Arrays.stream(GitFixtures.run(repository, "diff-tree", "-r", "--no-commit-id", "--name-status", "--no-renames", revision).split("\\R"))
                .filter(line -> !line.isBlank()).map(line -> line.replace('\t', ' ')).collect(Collectors.toSet());
    }

    @Test
    void candidateReleaseAndNextSnapshotAreCommittedWithOnlyWhatChangedAndTheRepositoryEndsOnTheExactTree() throws IOException {
        prepare();
        publish("26.3-rc-3", Map.of());
        PublicationResult release = publish("26.3", Map.of(DataRepositoryRules.STEP_DIFF_FILE, diffBetween("26.3-rc-3", "26.3")));
        assertEquals(Set.of("A versions/26.3/meta.json", "A diffs/26.3/step.json"), committed("HEAD"));
        assertEquals(0, release.raw().added() + release.raw().modified() + release.raw().removed());
        assertEquals("", GitFixtures.run(repository, "diff", "--stat", "26.3-rc-3", "26.3", "--", "reports", "data"));
        PublicationResult snapshot = publish("26.4-snapshot-3", Map.of(DataRepositoryRules.STEP_DIFF_FILE, diffBetween("26.3", "26.4-snapshot-3")));
        assertEquals(0, snapshot.raw().removed());
        assertTrue(snapshot.raw().added() > 0);
        assertTrue(snapshot.raw().modified() > 0);
        assertTrue(snapshot.rawFiles() > snapshot.raw().added() + snapshot.raw().modified());
        List<String> expected = scanner.scan(new VersionId("26.4-snapshot-3"), normalized("26.4-snapshot-3"), DataAllowlist.Layout.NORMALIZED).stream()
                .filter(file -> !file.equals("meta.json")).sorted().toList();
        List<String> tracked = Arrays.stream(GitFixtures.run(repository, "ls-files", "reports", "data").split("\\R")).filter(line -> !line.isBlank()).sorted().toList();
        assertEquals(expected, tracked);
        assertEquals("", GitFixtures.status(repository));
        assertEquals(List.of("26.3", "26.3-rc-3", "26.4-snapshot-3"), GitFixtures.run(repository, "tag", "--list").lines().sorted().toList());
        assertEquals(snapshot.raw().added() + snapshot.raw().modified(),
                (int) GitFixtures.run(repository, "diff", "--name-only", "26.3", "26.4-snapshot-3", "--", "reports", "data").lines().filter(line -> !line.isBlank()).count());
        for (String file : expected) {
            assertEquals(-1L, Files.mismatch(normalized("26.4-snapshot-3").resolve(file), repository.resolve(file)), file);
        }
        assertFalse(Files.exists(repository.resolve("diffs/26.3/release.json")));
    }
}
