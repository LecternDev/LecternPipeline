package tlm.dev.lectern.pipeline.publish;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlm.dev.lectern.pipeline.fixtures.FakeLauncher;
import tlm.dev.lectern.pipeline.fixtures.GitFixtures;
import tlm.dev.lectern.pipeline.fixtures.TreeFixtures;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.process.JdkProcessLauncher;
import tlm.dev.lectern.pipeline.process.ProcessOutcome;

class PublicationServiceTest {

    private static final VersionId ID = new VersionId("99.1");
    private static final List<String> FILES = List.of("data/demo/recipe/thing.json", "meta.json", "reports/gadgets.json");

    @TempDir
    Path temporary;

    private Path repository;
    private Path normalized;
    private PublicationService service;

    @BeforeEach
    void setUp() {
        assumeTrue(GitFixtures.gitAvailable(), "git is not available on this machine, install it to run the publication tests");
        repository = GitFixtures.initRepository(temporary.resolve("data-repository"));
        normalized = temporary.resolve("normalized").resolve("99.1");
        TreeFixtures.write(normalized, "meta.json", "{\n  \"id\": \"99.1\"\n}\n");
        TreeFixtures.write(normalized, "reports/gadgets.json", "{\n  \"demo:chest\": 0\n}\n");
        TreeFixtures.write(normalized, "data/demo/recipe/thing.json", "{\n  \"a\": 0.50\n}\n");
        service = new PublicationService(new GitClient(new JdkProcessLauncher(), Path.of("git"), Duration.ofMinutes(2)), temporary.resolve("scratch"));
    }

    private Set<String> committedPaths(String revision) {
        String output = GitFixtures.run(repository, "diff-tree", "--root", "--no-commit-id", "--name-only", "-r", revision);
        return Arrays.stream(output.split("\\R")).filter(line -> !line.isBlank()).collect(Collectors.toSet());
    }

    @Test
    void publishCopiesCommitsExactlyTheFilesAndReportsTheCommit() throws IOException {
        PublicationResult result = service.publish(ID, normalized, FILES, repository);
        assertEquals(3, result.files());
        assertEquals(GitFixtures.run(repository, "rev-parse", "HEAD").strip(), result.commit());
        assertEquals(Set.of("versions/99.1/meta.json", "versions/99.1/reports/gadgets.json", "versions/99.1/data/demo/recipe/thing.json"), committedPaths("HEAD"));
        for (String relative : FILES) {
            assertArrayEquals(Files.readAllBytes(normalized.resolve(relative)), Files.readAllBytes(repository.resolve("versions/99.1").resolve(relative)), relative);
        }
    }

    @Test
    void commitHasASingleLineTitleNoTrailerAndTheRepositoryIdentity() {
        service.publish(ID, normalized, FILES, repository);
        assertEquals("data: 99.1\n", GitFixtures.run(repository, "log", "-1", "--pretty=%B").stripTrailing() + "\n");
        assertEquals(1, GitFixtures.run(repository, "log", "-1", "--pretty=%B").lines().filter(line -> !line.isBlank()).count());
        assertEquals(GitFixtures.NAME + " <" + GitFixtures.EMAIL + ">", GitFixtures.run(repository, "log", "-1", "--pretty=%an <%ae>").strip());
        assertEquals("", GitFixtures.run(repository, "log", "-1", "--pretty=%b").strip());
    }

    @Test
    void workingTreeIsCleanAfterwardsAndTheScratchFolderIsGone() throws IOException {
        service.publish(ID, normalized, FILES, repository);
        assertEquals("", GitFixtures.run(repository, "status", "--porcelain"));
        try (var children = Files.list(temporary.resolve("scratch"))) {
            assertEquals(List.of(), children.toList());
        }
    }

    @Test
    void bytesStayExactDespiteCoreAutocrlfThanksToTheAttributes() throws IOException {
        TreeFixtures.write(normalized, "reports/gadgets.json", "{\n  \"text\": \"line\\nbreak\"\n}\n");
        service.publish(ID, normalized, FILES, repository);
        GitFixtures.run(repository, "config", "--local", "core.autocrlf", "true");
        Files.delete(repository.resolve("versions/99.1/reports/gadgets.json"));
        GitFixtures.run(repository, "checkout", "--", "versions/99.1/reports/gadgets.json");
        assertEquals("{\n  \"text\": \"line\\nbreak\"\n}\n", TreeFixtures.read(repository.resolve("versions/99.1/reports/gadgets.json")));
    }

    @Test
    void secondVersionGetsItsOwnCommitAndFolder() {
        service.publish(ID, normalized, FILES, repository);
        VersionId next = new VersionId("99.2");
        Path nextNormalized = temporary.resolve("normalized").resolve("99.2");
        for (String relative : FILES) {
            TreeFixtures.write(nextNormalized, relative, TreeFixtures.read(normalized.resolve(relative)));
        }
        service.publish(next, nextNormalized, FILES, repository);
        assertEquals(List.of("data: 99.2", "data: 99.1", "chore: prepare the data repository"),
                GitFixtures.run(repository, "log", "--format=%s").lines().toList());
        assertEquals(3, committedPaths("HEAD").size());
    }

    @Test
    void nothingIsEverPushed() throws IOException {
        Path bare = temporary.resolve("remote.git");
        GitFixtures.run(temporary, "init", "-q", "--bare", "-b", "master", bare.toString());
        GitFixtures.run(repository, "remote", "add", "origin", bare.toString());
        service.publish(ID, normalized, FILES, repository);
        assertEquals("0", GitFixtures.run(bare, "rev-list", "--all", "--count").strip());
    }

    @Test
    void otherFilesOfTheRepositoryAreNeverCommitted() {
        TreeFixtures.write(repository, "notes.txt", "local notes");
        TreeFixtures.write(repository, "versions/99.0/reports/old.json", "{}");
        service.publish(ID, normalized, FILES, repository);
        assertEquals(Set.of("versions/99.1/meta.json", "versions/99.1/reports/gadgets.json", "versions/99.1/data/demo/recipe/thing.json"), committedPaths("HEAD"));
        assertTrue(GitFixtures.run(repository, "status", "--porcelain").contains("notes.txt"));
    }

    @Test
    void existingVersionFolderIsRefusedAndNothingChanges() {
        TreeFixtures.write(repository, "versions/99.1/reports/old.json", "{}");
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(ID, normalized, FILES, repository));
        assertTrue(failure.getMessage().contains("already exists"), failure.getMessage());
        assertEquals(List.of("chore: prepare the data repository"), GitFixtures.run(repository, "log", "--format=%s").lines().toList());
        assertEquals(List.of("reports/old.json"), TreeFixtures.filesUnder(repository.resolve("versions/99.1")));
    }

    @Test
    void folderThatIsNotAGitRepositoryIsRefused() throws IOException {
        Path plain = Files.createDirectories(temporary.resolve("plain"));
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(ID, normalized, FILES, plain));
        assertTrue(failure.getMessage().contains("not a git repository"), failure.getMessage());
        assertEquals(List.of(), TreeFixtures.filesUnder(plain));
    }

    @Test
    void missingFolderIsRefused() {
        assertThrows(PublicationException.class, () -> service.publish(ID, normalized, FILES, temporary.resolve("absent")));
    }

    @Test
    void subfolderOfARepositoryIsRefusedBecauseItIsNotTheTopLevel() throws IOException {
        Path sub = Files.createDirectories(repository.resolve("inside"));
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(ID, normalized, FILES, sub));
        assertTrue(failure.getMessage().contains("not the top level"), failure.getMessage());
        assertEquals(List.of(), TreeFixtures.filesUnder(sub));
    }

    @Test
    void repositoryWithoutTheAttributesIsRefusedWithTheLinesToAdd() {
        Path bare = GitFixtures.initRepository(temporary.resolve("no-attributes"), false);
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(ID, normalized, FILES, bare));
        assertTrue(failure.getMessage().contains("versions/** -text"), failure.getMessage());
        assertTrue(failure.getMessage().contains("diffs/** -text"), failure.getMessage());
        assertEquals(List.of(), TreeFixtures.filesUnder(bare.resolve("versions")));
    }

    @Test
    void repositoryWithAlreadyStagedChangesIsRefused() {
        TreeFixtures.write(repository, "staged.txt", "x");
        GitFixtures.run(repository, "add", "staged.txt");
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(ID, normalized, FILES, repository));
        assertTrue(failure.getMessage().contains("staged changes"), failure.getMessage());
        assertFalse(Files.exists(repository.resolve("versions/99.1")));
    }

    @Test
    void requirePublishableChecksWithoutWritingAnything() {
        service.requirePublishable(ID, repository);
        assertFalse(Files.exists(repository.resolve("versions")));
        TreeFixtures.write(repository, "versions/99.1/x.json", "{}");
        assertThrows(PublicationException.class, () -> service.requirePublishable(ID, repository));
    }

    @Test
    void commitHookThatChangesTheMessageIsReportedAndTheCommitIsKept() {
        GitFixtures.installHook(repository, "commit-msg", "#!/bin/sh\nprintf '\\nCo-Authored-By: Invented <invented@example.invalid>\\n' >> \"$1\"\n");
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(ID, normalized, FILES, repository));
        assertTrue(failure.getMessage().contains("not the single line 'data: 99.1'"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Co-Authored-By"), failure.getMessage());
        assertTrue(GitFixtures.run(repository, "log", "-1", "--pretty=%B").contains("Co-Authored-By"));
    }

    @Test
    void failingCommitUnstagesWhatWasAddedAndRemovesTheCopiedFolder() {
        Path hook = GitFixtures.installHook(repository, "pre-commit", "#!/bin/sh\nexit 1\n");
        GitException failure = assertThrows(GitException.class, () -> service.publish(ID, normalized, FILES, repository));
        assertTrue(failure.getMessage().contains("commit"), failure.getMessage());
        assertEquals("", GitFixtures.run(repository, "diff", "--cached", "--name-only"));
        assertEquals(List.of("chore: prepare the data repository"), GitFixtures.run(repository, "log", "--format=%s").lines().toList());
        assertFalse(Files.exists(repository.resolve("versions/99.1")));
        assertEquals("", GitFixtures.run(repository, "status", "--porcelain"));
        assertTrue(Files.isRegularFile(normalized.resolve("meta.json")));
        assertTrue(hook.toFile().delete());
        service.publish(ID, normalized, FILES, repository);
        assertEquals("data: 99.1", GitFixtures.run(repository, "log", "-1", "--pretty=%s").strip());
    }

    @Test
    void failingAddRemovesTheCopiedFolderSoThatTheVersionCanBePublishedAgain() {
        PublicationService failingAdd = new PublicationService(new GitClient(realGitExcept("add"), Path.of("git"), Duration.ofMinutes(2)), temporary.resolve("scratch"));
        GitException failure = assertThrows(GitException.class, () -> failingAdd.publish(ID, normalized, FILES, repository));
        assertTrue(failure.getMessage().contains("git add"), failure.getMessage());
        assertFalse(Files.exists(repository.resolve("versions/99.1")));
        assertEquals("", GitFixtures.run(repository, "status", "--porcelain"));
        service.publish(ID, normalized, FILES, repository);
        assertEquals(Set.of("versions/99.1/meta.json", "versions/99.1/reports/gadgets.json", "versions/99.1/data/demo/recipe/thing.json"), committedPaths("HEAD"));
    }

    @Test
    void fileSkippedBecauseTheRepositoryIgnoresItStopsBeforeTheCommitAndRemovesTheCopy() {
        TreeFixtures.write(repository, ".gitignore", "gadgets.json\n");
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(ID, normalized, FILES, repository));
        assertTrue(failure.getMessage().contains("staged files are not exactly the published ones"), failure.getMessage());
        assertTrue(failure.getMessage().contains("versions/99.1/reports/gadgets.json"), failure.getMessage());
        assertEquals(List.of("chore: prepare the data repository"), GitFixtures.run(repository, "log", "--format=%s").lines().toList());
        assertEquals("", GitFixtures.run(repository, "diff", "--cached", "--name-only"));
        assertFalse(Files.exists(repository.resolve("versions/99.1")));
    }

    @Test
    void hookThatStagesAnotherFileDuringTheCommitIsReportedAsUnexpectedFiles() {
        TreeFixtures.write(repository, "extra.txt", "x");
        GitFixtures.installHook(repository, "pre-commit", "#!/bin/sh\ngit add extra.txt\n");
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(ID, normalized, FILES, repository));
        assertTrue(failure.getMessage().contains("the committed files are not exactly the published ones"), failure.getMessage());
        assertTrue(failure.getMessage().contains("extra.txt"), failure.getMessage());
    }

    @Test
    void failingUnstageKeepsTheCopiedFolderAndReportsBothFailures() {
        GitFixtures.installHook(repository, "pre-commit", "#!/bin/sh\nexit 1\n");
        PublicationService failingReset = new PublicationService(new GitClient(realGitExcept("reset"), Path.of("git"), Duration.ofMinutes(2)), temporary.resolve("scratch"));
        GitException failure = assertThrows(GitException.class, () -> failingReset.publish(ID, normalized, FILES, repository));
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("git reset"), failure.getSuppressed()[0].getMessage());
        assertTrue(Files.isRegularFile(repository.resolve("versions/99.1/meta.json")));
    }

    @Test
    void commitThatWasMadeIsNeverRemovedWhenALaterCheckFails() {
        GitFixtures.installHook(repository, "commit-msg", "#!/bin/sh\nprintf '\\nExtra line\\n' >> \"$1\"\n");
        assertThrows(PublicationException.class, () -> service.publish(ID, normalized, FILES, repository));
        assertTrue(Files.isRegularFile(repository.resolve("versions/99.1/meta.json")));
        assertEquals(committedPaths("HEAD"), Set.of("versions/99.1/meta.json", "versions/99.1/reports/gadgets.json", "versions/99.1/data/demo/recipe/thing.json"));
    }

    private static FakeLauncher realGitExcept(String subcommand) {
        JdkProcessLauncher real = new JdkProcessLauncher();
        return new FakeLauncher(call -> {
            if (call.command().contains(subcommand)) {
                return new ProcessOutcome.Exited(1, "", "fatal: invented failure of " + subcommand);
            }
            return real.launch(call.command(), call.workingDirectory(), call.timeout());
        });
    }

    @Test
    void draftLeftByAFailedPublicationIsReplaced() {
        TreeFixtures.write(repository, "versions/99.1.partial/stale.json", "{}");
        service.publish(ID, normalized, FILES, repository);
        assertFalse(Files.exists(repository.resolve("versions/99.1.partial")));
        assertFalse(committedPaths("HEAD").contains("versions/99.1/stale.json"));
    }

    @Test
    void pathThatLeavesTheFolderIsRefusedBeforeTheFolderAppears() {
        List<String> files = List.of("meta.json", "../escape.json");
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(ID, normalized, files, repository));
        assertTrue(failure.getMessage().contains("../escape.json"), failure.getMessage());
        assertFalse(Files.exists(repository.resolve("versions/99.1")));
        assertFalse(Files.exists(temporary.resolve("escape.json")));
    }

    @Test
    void messageIsExactlyTheTitleWhateverTheIdLooksLike() {
        VersionId snapshot = new VersionId("99.4-snapshot-3");
        Path folder = temporary.resolve("normalized").resolve("99.4-snapshot-3");
        for (String relative : FILES) {
            TreeFixtures.write(folder, relative, TreeFixtures.read(normalized.resolve(relative)));
        }
        service.publish(snapshot, folder, FILES, repository);
        assertEquals("data: 99.4-snapshot-3", GitFixtures.run(repository, "log", "-1", "--pretty=%s").strip());
        assertTrue(StandardCharsets.UTF_8.newEncoder().canEncode("data: 99.4-snapshot-3"));
    }
}
