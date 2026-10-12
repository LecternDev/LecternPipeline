package tlm.dev.lectern.pipeline.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tlm.dev.lectern.pipeline.fixtures.FakeLauncher;
import tlm.dev.lectern.pipeline.fixtures.GitFixtures;
import tlm.dev.lectern.pipeline.fixtures.NormalizedFixtures;
import tlm.dev.lectern.pipeline.fixtures.TreeFixtures;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.process.JdkProcessLauncher;
import tlm.dev.lectern.pipeline.process.ProcessLauncher;
import tlm.dev.lectern.pipeline.process.ProcessOutcome;

class PublicationServiceTest {

    private static final VersionId FIRST = new VersionId("99.1");
    private static final VersionId SECOND = new VersionId("99.2");
    private static final String GADGETS = "reports/gadgets.json";
    private static final String LAMP = "reports/minecraft/components/item/demo_lamp.json";
    private static final String THING = "data/demo/recipe/thing.json";
    private static final String EXTRA = "reports/extra.json";
    private static final Map<String, String> FIRST_RAW = Map.of(
            GADGETS, "{\n  \"demo:chest\": 0\n}\n",
            LAMP, "{\n  \"components\": {}\n}\n",
            THING, "{\n  \"a\": 0.50\n}\n");
    private static final Map<String, String> SECOND_RAW = Map.of(
            GADGETS, "{\n  \"demo:chest\": 0,\n  \"demo:lamp\": 1\n}\n",
            LAMP, "{\n  \"components\": {}\n}\n",
            EXTRA, "{}\n");

    @TempDir
    Path temporary;

    private Path repository;
    private Path normalizedRoot;
    private PublicationService service;

    @BeforeEach
    void setUp() {
        assumeTrue(GitFixtures.gitAvailable(), "git is not available on this machine, install it to run the publication tests");
        repository = GitFixtures.initRepository(temporary.resolve("data-repository"));
        normalizedRoot = temporary.resolve("normalized");
        service = serviceOver(new JdkProcessLauncher());
    }

    private PublicationService serviceOver(ProcessLauncher launcher) {
        GitClient git = new GitClient(launcher, Path.of("git"), Duration.ofMinutes(2));
        return new PublicationService(git, new RepositoryGuard(git), new TreeUpdater(), temporary.resolve("scratch"));
    }

    private static FakeLauncher realGitExcept(String subcommand) {
        return realGitFailingWhen(subcommand, command -> command.contains(subcommand));
    }

    private static FakeLauncher realGitFailingWhen(String name, Predicate<List<String>> failing) {
        JdkProcessLauncher real = new JdkProcessLauncher();
        return new FakeLauncher(call -> {
            if (failing.test(call.command())) {
                return new ProcessOutcome.Exited(1, "", "fatal: invented failure of " + name);
            }
            return real.launch(call.command(), call.workingDirectory(), call.timeout());
        });
    }

    private PublicationRequest request(VersionId id, Map<String, String> raw, Map<String, String> diffs) {
        Path folder = NormalizedFixtures.write(normalizedRoot, id.value(), raw);
        Map<String, byte[]> bytes = diffs.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getBytes(StandardCharsets.UTF_8)));
        return new PublicationRequest(id, folder, TreeFixtures.filesUnder(folder), bytes, repository);
    }

    private PublicationRequest request(VersionId id, Map<String, String> raw) {
        return request(id, raw, Map.of(DataRepositoryRules.STEP_DIFF_FILE, "{\"to\": \"" + id.value() + "\"}\n"));
    }

    private Set<String> committed(String revision) {
        String output = GitFixtures.run(repository, "diff-tree", "-r", "--no-commit-id", "--name-status", "--no-renames", revision);
        return Arrays.stream(output.split("\\R")).filter(line -> !line.isBlank()).map(line -> line.replace('\t', ' ')).collect(Collectors.toSet());
    }

    private String filesAt(String revision) {
        return Arrays.stream(GitFixtures.run(repository, "ls-tree", "-r", "--name-only", revision).split("\\R")).sorted().collect(Collectors.joining(","));
    }

    private void assertUntouched(String headBefore) {
        assertEquals(headBefore, GitFixtures.head(repository));
        assertEquals("", GitFixtures.status(repository));
    }

    @Test
    void firstPublicationPlacesTheRawFilesAtTheRootAndTheSheetAndTheDiffsUnderTheirFolders() {
        Map<String, String> diffs = Map.of(DataRepositoryRules.STEP_DIFF_FILE, "{\"step\": 1}\n", DataRepositoryRules.RELEASE_DIFF_FILE, "{\"release\": 1}\n");
        PublicationResult result = service.publish(request(FIRST, FIRST_RAW, diffs));
        assertEquals(Set.of("A reports/gadgets.json", "A reports/minecraft/components/item/demo_lamp.json", "A data/demo/recipe/thing.json",
                "A versions/99.1/meta.json", "A diffs/99.1/step.json", "A diffs/99.1/release.json"), committed("HEAD"));
        assertEquals(GitFixtures.head(repository), result.commit());
        assertEquals(3, result.rawFiles());
        assertEquals(new TreeChanges(3, 0, 0), result.raw());
        assertEquals(List.of("step.json", "release.json"), result.diffFiles());
        assertEquals("{\n  \"demo:chest\": 0\n}\n", TreeFixtures.read(repository.resolve(GADGETS)));
        assertEquals("{\"step\": 1}\n", TreeFixtures.read(repository.resolve("diffs/99.1/step.json")));
        assertEquals("", GitFixtures.status(repository));
    }

    @Test
    void tagWithTheNameOfTheVersionIsCreatedOnTheNewCommit() {
        service.publish(request(FIRST, FIRST_RAW));
        assertEquals("99.1", GitFixtures.run(repository, "tag", "--list").strip());
        assertEquals(GitFixtures.head(repository), GitFixtures.run(repository, "rev-parse", "99.1^{commit}").strip());
        assertEquals("commit", GitFixtures.run(repository, "cat-file", "-t", "99.1").strip());
    }

    @Test
    void commitHasASingleLineTitleNoTrailerAndTheRepositoryIdentity() {
        service.publish(request(FIRST, FIRST_RAW));
        assertEquals("data: 99.1", GitFixtures.run(repository, "log", "-1", "--pretty=%B").strip());
        assertEquals(GitFixtures.NAME + " <" + GitFixtures.EMAIL + ">", GitFixtures.run(repository, "log", "-1", "--format=%an <%ae>").strip());
    }

    @Test
    void bytesStayExactDespiteCoreAutocrlfThanksToTheAttributes() {
        String crlf = "{\r\n  \"a\": 1\r\n}\r\n";
        service.publish(request(FIRST, Map.of(GADGETS, crlf)));
        assertEquals(crlf, GitFixtures.run(repository, "cat-file", "blob", "HEAD:" + GADGETS));
        assertEquals(crlf, TreeFixtures.read(repository.resolve(GADGETS)));
    }

    @Test
    void nextVersionReplacesTheRawTreeAndCommitsOnlyWhatChanged() {
        service.publish(request(FIRST, FIRST_RAW));
        PublicationResult result = service.publish(request(SECOND, SECOND_RAW));
        assertEquals(Set.of("M reports/gadgets.json", "A reports/extra.json", "D data/demo/recipe/thing.json",
                "A versions/99.2/meta.json", "A diffs/99.2/step.json"), committed("HEAD"));
        assertEquals(new TreeChanges(1, 1, 1), result.raw());
        assertEquals(3, result.rawFiles());
        assertEquals(Set.of("M reports/gadgets.json", "A reports/extra.json", "D data/demo/recipe/thing.json"),
                Arrays.stream(GitFixtures.run(repository, "diff", "--name-status", "99.1", "99.2", "--", "reports", "data").split("\\R"))
                        .map(line -> line.replace('\t', ' ')).collect(Collectors.toSet()));
        assertFalse(Files.exists(repository.resolve("data")));
        assertEquals("", GitFixtures.status(repository));
    }

    @Test
    void earlierSheetsAndDiffsStayWhileTheRawTreeMovesOn() {
        service.publish(request(FIRST, FIRST_RAW));
        service.publish(request(SECOND, SECOND_RAW));
        assertTrue(Files.isRegularFile(repository.resolve("versions/99.1/meta.json")));
        assertTrue(Files.isRegularFile(repository.resolve("diffs/99.1/step.json")));
        assertTrue(Files.isRegularFile(repository.resolve("versions/99.2/meta.json")));
        assertEquals("diffs/99.1/step.json,diffs/99.2/step.json,reports/extra.json,reports/gadgets.json,reports/minecraft/components/item/demo_lamp.json,"
                + "versions/99.1/meta.json,versions/99.2/meta.json", filesAt("99.2").replace(".gitattributes,", ""));
        assertEquals(FIRST_RAW.get(THING), GitFixtures.run(repository, "cat-file", "blob", "99.1:" + THING));
    }

    @Test
    void versionWithTheSameRawDataCommitsOnlyItsSheetAndItsDiffs() {
        service.publish(request(FIRST, FIRST_RAW));
        PublicationResult result = service.publish(request(SECOND, FIRST_RAW));
        assertEquals(Set.of("A versions/99.2/meta.json", "A diffs/99.2/step.json"), committed("HEAD"));
        assertEquals(new TreeChanges(0, 0, 0), result.raw());
        assertEquals("", GitFixtures.run(repository, "diff", "--stat", "99.1", "99.2", "--", "reports", "data"));
    }

    @Test
    void versionWithoutDiffsWritesNoDiffFolder() {
        PublicationResult result = service.publish(request(FIRST, FIRST_RAW, Map.of()));
        assertEquals(List.of(), result.diffFiles());
        assertFalse(Files.exists(repository.resolve("diffs")));
        assertEquals(Set.of("A reports/gadgets.json", "A reports/minecraft/components/item/demo_lamp.json", "A data/demo/recipe/thing.json",
                "A versions/99.1/meta.json"), committed("HEAD"));
    }

    @Test
    void filesThatAreAlreadyIdenticalAreNotRewritten() throws IOException {
        service.publish(request(FIRST, FIRST_RAW));
        FileTime old = FileTime.fromMillis(1_000_000_000_000L);
        Files.setLastModifiedTime(repository.resolve(LAMP), old);
        service.publish(request(SECOND, SECOND_RAW));
        assertEquals(old, Files.getLastModifiedTime(repository.resolve(LAMP)));
    }

    @Test
    void nothingIsEverPushed() throws IOException {
        Path remote = temporary.resolve("remote.git");
        GitFixtures.run(temporary, "init", "-q", "--bare", remote.toString());
        GitFixtures.run(repository, "remote", "add", "origin", remote.toString());
        service.publish(request(FIRST, FIRST_RAW));
        assertEquals("", GitFixtures.run(remote, "for-each-ref"));
    }

    @Test
    void publishingTheSameVersionTwiceIsRefusedAndChangesNothing() {
        service.publish(request(FIRST, FIRST_RAW));
        String head = GitFixtures.head(repository);
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(request(FIRST, SECOND_RAW)));
        assertTrue(failure.getMessage().contains("published already"), failure.getMessage());
        assertUntouched(head);
    }

    @Test
    void requirePublishableChecksWithoutWritingAnything() {
        String head = GitFixtures.head(repository);
        service.requirePublishable(FIRST, repository);
        assertUntouched(head);
        assertFalse(Files.exists(repository.resolve("reports")));
        GitFixtures.run(repository, "tag", "99.1");
        assertThrows(PublicationException.class, () -> service.requirePublishable(FIRST, repository));
    }

    @Test
    void repositoryWithAModifiedTrackedFileIsRefused() {
        GitFixtures.commitFile(repository, "notes.txt", "one", "chore: notes");
        TreeFixtures.write(repository, "notes.txt", "two");
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(request(FIRST, FIRST_RAW)));
        assertTrue(failure.getMessage().contains("not clean"), failure.getMessage());
        assertTrue(failure.getMessage().contains("notes.txt"), failure.getMessage());
        assertFalse(Files.exists(repository.resolve("reports")));
    }

    @Test
    void repositoryWithAnUntrackedFileIsRefused() {
        TreeFixtures.write(repository, "scratch/notes.txt", "x");
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(request(FIRST, FIRST_RAW)));
        assertTrue(failure.getMessage().contains("not clean"), failure.getMessage());
        assertTrue(failure.getMessage().contains("scratch/notes.txt"), failure.getMessage());
        assertFalse(Files.exists(repository.resolve("reports")));
    }

    @Test
    void repositoryWithStagedChangesIsRefused() {
        TreeFixtures.write(repository, "staged.txt", "x");
        GitFixtures.run(repository, "add", "staged.txt");
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(request(FIRST, FIRST_RAW)));
        assertTrue(failure.getMessage().contains("not clean"), failure.getMessage());
        assertFalse(Files.exists(repository.resolve("reports")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"reports/** -text", "data/** -text", "versions/** -text", "diffs/** -text"})
    void repositoryThatLacksOneOfTheAttributeLinesIsRefusedWithTheLinesToAdd(String missing) {
        String kept = Arrays.stream(DataRepositoryRules.GITATTRIBUTES.split("\n")).filter(line -> !line.equals(missing)).collect(Collectors.joining("\n", "", "\n"));
        GitFixtures.commitFile(repository, ".gitattributes", kept, "chore: weaken the attributes");
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(request(FIRST, FIRST_RAW)));
        assertTrue(failure.getMessage().contains("text conversion off"), failure.getMessage());
        for (String line : DataRepositoryRules.GITATTRIBUTES.split("\n")) {
            assertTrue(failure.getMessage().contains(line), failure.getMessage());
        }
        assertFalse(Files.exists(repository.resolve("reports")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"reports", "data", "versions", "diffs"})
    void managedFolderThatIsALinkIsRefused(String name) throws IOException {
        Path elsewhere = Files.createDirectories(temporary.resolve("elsewhere"));
        assumeTrue(TreeFixtures.tryFolderLink(repository.resolve(name), elsewhere), "this machine can create neither symbolic links nor junctions");
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(request(FIRST, FIRST_RAW)));
        assertTrue(failure.getMessage().contains("plain folder"), failure.getMessage());
        assertTrue(failure.getMessage().contains(name), failure.getMessage());
        try (var children = Files.list(elsewhere)) {
            assertEquals(0, children.count());
        }
    }

    @Test
    void repositoryWithoutAnyAttributesIsRefused() {
        Path bare = GitFixtures.initRepository(temporary.resolve("bare"), false);
        GitFixtures.commitFile(bare, "readme.txt", "x", "chore: first");
        PublicationException failure = assertThrows(PublicationException.class,
                () -> service.publish(new PublicationRequest(FIRST, request(FIRST, FIRST_RAW).normalizedFolder(), List.of("meta.json"), Map.of(), bare)));
        assertTrue(failure.getMessage().contains("text conversion off"), failure.getMessage());
    }

    @Test
    void existingVersionSheetFolderIsRefused() {
        GitFixtures.commitFile(repository, "versions/99.1/meta.json", "{}\n", "chore: stray sheet");
        String head = GitFixtures.head(repository);
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(request(FIRST, FIRST_RAW)));
        assertTrue(failure.getMessage().contains("versions"), failure.getMessage());
        assertTrue(failure.getMessage().contains("already exists"), failure.getMessage());
        assertUntouched(head);
    }

    @Test
    void existingDiffFolderIsRefused() {
        GitFixtures.commitFile(repository, "diffs/99.1/step.json", "{}\n", "chore: stray diff");
        String head = GitFixtures.head(repository);
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(request(FIRST, FIRST_RAW)));
        assertTrue(failure.getMessage().contains("diffs"), failure.getMessage());
        assertUntouched(head);
    }

    @Test
    void existingTagIsRefused() {
        GitFixtures.run(repository, "tag", "99.1");
        String head = GitFixtures.head(repository);
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(request(FIRST, FIRST_RAW)));
        assertTrue(failure.getMessage().contains("tag 99.1 already exists"), failure.getMessage());
        assertUntouched(head);
    }

    @Test
    void folderThatIsNotAGitRepositoryIsRefused() throws IOException {
        Path plain = Files.createDirectories(temporary.resolve("plain"));
        PublicationRequest request = request(FIRST, FIRST_RAW);
        PublicationException failure = assertThrows(PublicationException.class,
                () -> service.publish(new PublicationRequest(FIRST, request.normalizedFolder(), request.files(), request.diffs(), plain)));
        assertTrue(failure.getMessage().contains("not a git repository"), failure.getMessage());
    }

    @Test
    void missingFolderIsRefused() {
        PublicationRequest request = request(FIRST, FIRST_RAW);
        assertThrows(PublicationException.class,
                () -> service.publish(new PublicationRequest(FIRST, request.normalizedFolder(), request.files(), request.diffs(), temporary.resolve("absent"))));
    }

    @Test
    void subfolderOfARepositoryIsRefusedBecauseItIsNotTheTopLevel() throws IOException {
        Path sub = Files.createDirectories(repository.resolve("nested"));
        PublicationRequest request = request(FIRST, FIRST_RAW);
        PublicationException failure = assertThrows(PublicationException.class,
                () -> service.publish(new PublicationRequest(FIRST, request.normalizedFolder(), request.files(), request.diffs(), sub)));
        assertTrue(failure.getMessage().contains("not the top level"), failure.getMessage());
    }

    @Test
    void repositoryWithoutAnyCommitIsRefused() {
        Path empty = GitFixtures.initRepository(temporary.resolve("empty"), false);
        PublicationRequest request = request(FIRST, FIRST_RAW);
        PublicationException failure = assertThrows(PublicationException.class,
                () -> service.publish(new PublicationRequest(FIRST, request.normalizedFolder(), request.files(), request.diffs(), empty)));
        assertTrue(failure.getMessage().contains("no commit yet"), failure.getMessage());
    }

    @Test
    void failingCommitBringsTheRepositoryBackToItsStateBeforeTheRun() {
        service.publish(request(FIRST, FIRST_RAW));
        String head = GitFixtures.head(repository);
        Path hook = GitFixtures.installHook(repository, "pre-commit", "#!/bin/sh\nexit 1\n");
        GitException failure = assertThrows(GitException.class, () -> service.publish(request(SECOND, SECOND_RAW)));
        assertTrue(failure.getMessage().contains("commit"), failure.getMessage());
        assertUntouched(head);
        assertEquals(FIRST_RAW.get(THING), TreeFixtures.read(repository.resolve(THING)));
        assertEquals(FIRST_RAW.get(GADGETS), TreeFixtures.read(repository.resolve(GADGETS)));
        assertFalse(Files.exists(repository.resolve(EXTRA)));
        assertFalse(Files.exists(repository.resolve("versions/99.2")));
        assertFalse(Files.exists(repository.resolve("diffs/99.2")));
        assertEquals("99.1", GitFixtures.run(repository, "tag", "--list").strip());
        assertTrue(hook.toFile().delete());
        service.publish(request(SECOND, SECOND_RAW));
        assertEquals("data: 99.2", GitFixtures.run(repository, "log", "-1", "--pretty=%s").strip());
    }

    @Test
    void failingCommitOfTheFirstVersionLeavesNoFolderBehind() {
        String head = GitFixtures.head(repository);
        GitFixtures.installHook(repository, "pre-commit", "#!/bin/sh\nexit 1\n");
        assertThrows(GitException.class, () -> service.publish(request(FIRST, FIRST_RAW)));
        assertUntouched(head);
        for (String folder : List.of("reports", "data", "versions", "diffs")) {
            assertFalse(Files.exists(repository.resolve(folder)), folder);
        }
    }

    @Test
    void failingAddBringsTheRepositoryBackAndTheVersionCanBePublishedAgain() {
        service.publish(request(FIRST, FIRST_RAW));
        String head = GitFixtures.head(repository);
        GitException failure = assertThrows(GitException.class, () -> serviceOver(realGitExcept("add")).publish(request(SECOND, SECOND_RAW)));
        assertTrue(failure.getMessage().contains("git add"), failure.getMessage());
        assertUntouched(head);
        assertEquals(FIRST_RAW.get(THING), TreeFixtures.read(repository.resolve(THING)));
        service.publish(request(SECOND, SECOND_RAW));
        assertEquals("99.1\n99.2", GitFixtures.run(repository, "tag", "--list").strip().replace("\r", ""));
    }

    @Test
    void fileSkippedBecauseTheRepositoryIgnoresItStopsBeforeTheCommitAndIsRemoved() {
        service.publish(request(FIRST, FIRST_RAW));
        GitFixtures.commitFile(repository, ".gitignore", "extra.json\n", "chore: ignore a file");
        String head = GitFixtures.head(repository);
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(request(SECOND, SECOND_RAW)));
        assertTrue(failure.getMessage().contains("staged files are not exactly the published ones"), failure.getMessage());
        assertTrue(failure.getMessage().contains(EXTRA), failure.getMessage());
        assertUntouched(head);
        assertFalse(Files.exists(repository.resolve(EXTRA)));
        assertEquals(FIRST_RAW.get(THING), TreeFixtures.read(repository.resolve(THING)));
    }

    @Test
    void failingResetKeepsTheWrittenFilesAndReportsBothFailures() {
        service.publish(request(FIRST, FIRST_RAW));
        GitFixtures.installHook(repository, "pre-commit", "#!/bin/sh\nexit 1\n");
        GitException failure = assertThrows(GitException.class, () -> serviceOver(realGitExcept("reset")).publish(request(SECOND, SECOND_RAW)));
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("git reset"), failure.getSuppressed()[0].getMessage());
        assertTrue(Files.isRegularFile(repository.resolve(EXTRA)));
    }

    @Test
    void commitHookThatChangesTheMessageIsReportedAndTheCommitIsKept() {
        GitFixtures.installHook(repository, "commit-msg", "#!/bin/sh\nprintf '\\nCo-Authored-By: Invented <invented@example.invalid>\\n' >> \"$1\"\n");
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(request(FIRST, FIRST_RAW)));
        assertTrue(failure.getMessage().contains("not the single line 'data: 99.1'"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Co-Authored-By"), failure.getMessage());
        assertTrue(failure.getMessage().contains("create the tag 99.1"), failure.getMessage());
        assertTrue(GitFixtures.run(repository, "log", "-1", "--pretty=%B").contains("Co-Authored-By"));
        assertTrue(Files.isRegularFile(repository.resolve(GADGETS)));
        assertEquals("", GitFixtures.run(repository, "tag", "--list").strip());
    }

    @Test
    void hookThatStagesAnotherFileDuringTheCommitIsReportedAndTheCommitIsKept() {
        GitFixtures.installHook(repository, "pre-commit", "#!/bin/sh\necho x > extra.txt\ngit add extra.txt\n");
        PublicationException failure = assertThrows(PublicationException.class, () -> service.publish(request(FIRST, FIRST_RAW)));
        assertTrue(failure.getMessage().contains("the committed files are not exactly the staged ones"), failure.getMessage());
        assertTrue(failure.getMessage().contains("extra.txt"), failure.getMessage());
        assertEquals("", GitFixtures.run(repository, "tag", "--list").strip());
    }

    @Test
    void failingTagIsReportedWithTheCommandToRunByHand() {
        PublicationException failure = assertThrows(PublicationException.class, () -> serviceOver(realGitFailingWhen("tag", command -> command.contains("tag") && !command.contains("--list"))).publish(request(FIRST, FIRST_RAW)));
        String commit = GitFixtures.head(repository);
        assertTrue(failure.getMessage().contains("git tag 99.1 " + commit), failure.getMessage());
        assertEquals("data: 99.1", GitFixtures.run(repository, "log", "-1", "--pretty=%s").strip());
    }

    @Test
    void normalizedFolderWithoutASheetIsRefusedBeforeTheRepositoryChanges() {
        Path folder = normalizedRoot.resolve("99.1");
        TreeFixtures.write(folder, GADGETS, "{}\n");
        String head = GitFixtures.head(repository);
        PublicationException failure = assertThrows(PublicationException.class,
                () -> service.publish(new PublicationRequest(FIRST, folder, List.of(GADGETS), Map.of(), repository)));
        assertTrue(failure.getMessage().contains("meta.json"), failure.getMessage());
        assertUntouched(head);
    }

    @Test
    void pathThatLeavesTheFolderOrIsNotRawIsRefusedBeforeTheRepositoryChanges() {
        Path folder = NormalizedFixtures.write(normalizedRoot, "99.1", Map.of(GADGETS, "{}\n"));
        String head = GitFixtures.head(repository);
        for (String bad : List.of("reports/../escape.json", "other/file.json", "/reports/a.json", "reports\\a.json")) {
            PublicationException failure = assertThrows(PublicationException.class,
                    () -> service.publish(new PublicationRequest(FIRST, folder, List.of("meta.json", bad), Map.of(), repository)), bad);
            assertTrue(failure.getMessage().contains(bad), failure.getMessage());
            assertUntouched(head);
        }
        assertFalse(Files.exists(temporary.resolve("escape.json")));
    }

    @Test
    void messageIsExactlyTheTitleWhateverTheIdLooksLike() {
        VersionId snapshot = new VersionId("99.4-snapshot-3");
        service.publish(request(snapshot, FIRST_RAW));
        assertEquals("data: 99.4-snapshot-3", GitFixtures.run(repository, "log", "-1", "--pretty=%B").strip());
        assertEquals("99.4-snapshot-3", GitFixtures.run(repository, "tag", "--list").strip());
    }
}
