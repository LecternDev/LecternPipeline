package tlm.dev.lectern.pipeline.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlm.dev.lectern.pipeline.fixtures.TreeFixtures;

class TreeUpdaterTest {

    @TempDir
    Path temporary;

    private Path repository;
    private Path normalized;
    private final TreeUpdater updater = new TreeUpdater();

    @BeforeEach
    void setUp() {
        repository = temporary.resolve("repository");
        normalized = temporary.resolve("normalized");
    }

    @Test
    void newFilesAreCopiedWithTheirFolders() throws IOException {
        TreeFixtures.write(normalized, "reports/a.json", "{}\n");
        TreeFixtures.write(normalized, "data/deep/b.json", "{\"b\": 1}\n");
        updater.applyRaw(repository, normalized, new TreePlan(Set.of("reports/a.json", "data/deep/b.json"), Set.of()));
        assertEquals(List.of("data/deep/b.json", "reports/a.json"), TreeFixtures.filesUnder(repository));
        assertEquals("{\"b\": 1}\n", TreeFixtures.read(repository.resolve("data/deep/b.json")));
    }

    @Test
    void changedFilesAreReplacedAndIdenticalOnesAreLeftAlone() throws IOException {
        TreeFixtures.write(repository, "reports/same.json", "{}\n");
        TreeFixtures.write(repository, "reports/changed.json", "{\"v\": 1}\n");
        FileTime old = FileTime.fromMillis(1_000_000_000_000L);
        Files.setLastModifiedTime(repository.resolve("reports/same.json"), old);
        TreeFixtures.write(normalized, "reports/same.json", "{}\n");
        TreeFixtures.write(normalized, "reports/changed.json", "{\"v\": 2}\n");
        updater.applyRaw(repository, normalized, new TreePlan(Set.of("reports/same.json", "reports/changed.json"), Set.of("reports/same.json", "reports/changed.json")));
        assertEquals(old, Files.getLastModifiedTime(repository.resolve("reports/same.json")));
        assertEquals("{\"v\": 2}\n", TreeFixtures.read(repository.resolve("reports/changed.json")));
    }

    @Test
    void staleFilesAreDeletedAndTheirEmptyFoldersGoWithThem() throws IOException {
        TreeFixtures.write(repository, "data/old/deep/gone.json", "{}\n");
        TreeFixtures.write(repository, "data/kept/stay.json", "{}\n");
        TreeFixtures.write(normalized, "data/kept/stay.json", "{}\n");
        updater.applyRaw(repository, normalized, new TreePlan(Set.of("data/kept/stay.json"), Set.of("data/old/deep/gone.json", "data/kept/stay.json")));
        assertFalse(Files.exists(repository.resolve("data/old")));
        assertTrue(Files.isRegularFile(repository.resolve("data/kept/stay.json")));
    }

    @Test
    void folderThatHoldsOtherFilesIsKeptWhenAStaleFileLeaves() throws IOException {
        TreeFixtures.write(repository, "data/mixed/gone.json", "{}\n");
        TreeFixtures.write(repository, "data/mixed/untracked.txt", "x");
        updater.applyRaw(repository, normalized, new TreePlan(Set.of(), Set.of("data/mixed/gone.json")));
        assertEquals(List.of("data/mixed/untracked.txt"), TreeFixtures.filesUnder(repository));
    }

    @Test
    void copiedFileAndWrittenBytesGoToTheGivenRelativePath() throws IOException {
        TreeFixtures.write(normalized, "meta.json", "{\"id\": \"99.1\"}\n");
        updater.copyFile(repository, normalized.resolve("meta.json"), "versions/99.1/meta.json");
        updater.writeFile(repository, "diffs/99.1/step.json", new byte[] {'{', '}', '\n'});
        assertEquals("{\"id\": \"99.1\"}\n", TreeFixtures.read(repository.resolve("versions/99.1/meta.json")));
        assertEquals("{}\n", TreeFixtures.read(repository.resolve("diffs/99.1/step.json")));
    }

    @Test
    void removeDeletesTheGivenFilesIgnoresMissingOnesAndPrunesEmptyParents() throws IOException {
        TreeFixtures.write(repository, "reports/new/a.json", "{}\n");
        TreeFixtures.write(repository, "versions/99.1/meta.json", "{}\n");
        TreeFixtures.write(repository, "versions/98.9/meta.json", "{}\n");
        updater.remove(repository, Set.of("reports/new/a.json", "versions/99.1/meta.json", "diffs/99.1/step.json"));
        assertEquals(List.of("versions/98.9/meta.json"), TreeFixtures.filesUnder(repository));
        assertFalse(Files.exists(repository.resolve("reports")));
        assertFalse(Files.exists(repository.resolve("versions/99.1")));
        assertTrue(Files.isDirectory(repository.resolve("versions")));
    }

    @Test
    void repositoryFolderItselfIsNeverRemoved() throws IOException {
        TreeFixtures.write(repository, "reports/a.json", "{}\n");
        updater.remove(repository, Set.of("reports/a.json"));
        assertTrue(Files.isDirectory(repository));
    }
}
