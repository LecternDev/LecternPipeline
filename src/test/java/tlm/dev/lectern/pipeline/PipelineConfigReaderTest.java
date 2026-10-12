package tlm.dev.lectern.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tlm.dev.lectern.pipeline.model.VersionId;

class PipelineConfigReaderTest {

    private static final Path BASE = Path.of("lectern-test-base").toAbsolutePath();
    private static final Environment ENVIRONMENT = environment(false, Optional.empty());

    private final PipelineConfigReader reader = new PipelineConfigReader(ENVIRONMENT);

    private static Environment environment(boolean windows, Optional<String> diffCommand) {
        return new Environment(BASE.resolve("LecternPipeline"), BASE.resolve("jdk"), BASE.resolve("tmp"), windows, diffCommand);
    }

    @Test
    void listWithoutOptionUsesTheDefaultsOfTheEnvironment() {
        PipelineConfig config = reader.read(new String[] {"list"});
        assertInstanceOf(Command.ListVersions.class, config.command());
        assertEquals(URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"), config.manifestUrl());
        assertEquals(BASE.resolve("LecternWorkbench"), config.workbench());
        assertEquals(BASE.resolve("jdk").resolve("bin").resolve("java"), config.java());
        assertEquals(BASE.resolve("tmp"), config.scratchRoot());
    }

    @Test
    void defaultManifestUrlIsTheOfficialV2Manifest() {
        assertEquals("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json", PipelineConfigReader.DEFAULT_MANIFEST_URL);
    }

    @Test
    void fetchAndExtractTakeAVersionId() {
        assertEquals(new Command.Fetch(new VersionId("26.3")), reader.read(new String[] {"fetch", "26.3"}).command());
        assertEquals(new Command.Extract(new VersionId("26.3-rc-3")), reader.read(new String[] {"extract", "26.3-rc-3"}).command());
    }

    @Test
    void optionsMayFollowTheVersionId() {
        PipelineConfig config = reader.read(new String[] {"extract", "26.3", "--workbench=bench", "--manifest-url=http://127.0.0.1:8080/m", "--java=java"});
        assertEquals(new Command.Extract(new VersionId("26.3")), config.command());
        assertEquals(ENVIRONMENT.currentDirectory().resolve("bench"), config.workbench());
        assertEquals(URI.create("http://127.0.0.1:8080/m"), config.manifestUrl());
        assertEquals(Path.of("java"), config.java());
    }

    @Test
    void workbenchOptionIsResolvedAgainstTheCurrentDirectoryAndNormalized() {
        assertEquals(BASE.resolve("elsewhere"), reader.read(new String[] {"list", "--workbench=../elsewhere"}).workbench());
        Path absolute = BASE.resolve("absolute").resolve("bench");
        assertEquals(absolute, reader.read(new String[] {"list", "--workbench=" + absolute}).workbench());
    }

    @Test
    void manifestUrlOptionOverridesTheDefault() {
        PipelineConfig config = reader.read(new String[] {"list", "--manifest-url=http://127.0.0.1:8080/manifest"});
        assertEquals(URI.create("http://127.0.0.1:8080/manifest"), config.manifestUrl());
    }

    @Test
    void noArgumentIsAUsageError() {
        UsageException failure = assertThrows(UsageException.class, () -> reader.read(new String[] {}));
        assertEquals(PipelineConfigReader.USAGE, failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"download", "LIST", "--manifest-url=http://127.0.0.1/x", ""})
    void unknownCommandIsAUsageError(String command) {
        UsageException failure = assertThrows(UsageException.class, () -> reader.read(new String[] {command}));
        assertTrue(failure.getMessage().contains("Unknown command"), failure.getMessage());
        assertTrue(failure.getMessage().contains(PipelineConfigReader.USAGE), failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"fetch", "extract", "normalize", "publish"})
    void commandWithoutAVersionIdIsAUsageError(String command) {
        UsageException failure = assertThrows(UsageException.class, () -> reader.read(new String[] {command}));
        assertTrue(failure.getMessage().contains("needs a version id"), failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"fetch", "extract", "normalize", "publish"})
    void optionWhereTheVersionIdShouldBeIsAUsageError(String command) {
        assertThrows(UsageException.class, () -> reader.read(new String[] {command, "--workbench=x"}));
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", ".", "a/b", "a\\b", " 26.3", ""})
    void versionIdThatCannotNameAFolderIsAUsageError(String id) {
        UsageException failure = assertThrows(UsageException.class, () -> reader.read(new String[] {"extract", id}));
        assertTrue(failure.getMessage().contains("version id"), failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"--limit=3", "extra", "-manifest-url=http://127.0.0.1/x", "--data-dir=x"})
    void unknownArgumentAfterTheCommandIsAUsageError(String argument) {
        UsageException failure = assertThrows(UsageException.class, () -> reader.read(new String[] {"list", argument}));
        assertTrue(failure.getMessage().contains("Unknown argument"), failure.getMessage());
    }

    @Test
    void extraArgumentAfterTheVersionIdIsAUsageError() {
        assertThrows(UsageException.class, () -> reader.read(new String[] {"fetch", "26.3", "26.4"}));
    }

    @ParameterizedTest
    @ValueSource(strings = {"--manifest-url=http://127.0.0.1/a", "--workbench=a", "--java=a"})
    void optionGivenTwiceIsAUsageError(String option) {
        UsageException failure = assertThrows(UsageException.class, () -> reader.read(new String[] {"list", option, option}));
        assertTrue(failure.getMessage().contains("given twice"), failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not a url", "file:///etc/passwd", "ftp://127.0.0.1/manifest", "/relative/path", "http:///nohost", "mailto:someone@example.invalid"})
    void manifestUrlThatIsNotAWebUrlWithAHostIsAUsageError(String url) {
        assertThrows(UsageException.class, () -> reader.read(new String[] {"list", "--manifest-url=" + url}));
    }

    @Test
    void blankWorkbenchOrJavaOptionIsAUsageError() {
        assertThrows(UsageException.class, () -> reader.read(new String[] {"list", "--workbench="}));
        assertThrows(UsageException.class, () -> reader.read(new String[] {"list", "--java=  "}));
    }

    @Test
    void normalizeTakesAVersionId() {
        PipelineConfig config = reader.read(new String[] {"normalize", "26.3"});
        assertEquals(new Command.Normalize(new VersionId("26.3")), config.command());
        assertEquals(Path.of("git"), config.git());
    }

    @Test
    void publishNeedsADataRepositoryGivenWithEqualsOrWithASpace() {
        Path expected = ENVIRONMENT.currentDirectory().resolve("data-repo");
        assertEquals(new Command.Publish(new VersionId("26.3"), expected), reader.read(new String[] {"publish", "26.3", "--data=data-repo"}).command());
        assertEquals(new Command.Publish(new VersionId("26.3"), expected), reader.read(new String[] {"publish", "26.3", "--data", "data-repo"}).command());
        Path absolute = BASE.resolve("elsewhere").resolve("repo");
        assertEquals(new Command.Publish(new VersionId("26.3"), absolute), reader.read(new String[] {"publish", "26.3", "--data", absolute.toString()}).command());
    }

    @Test
    void publishWithoutTheDataOptionIsAUsageError() {
        UsageException failure = assertThrows(UsageException.class, () -> reader.read(new String[] {"publish", "26.3"}));
        assertTrue(failure.getMessage().contains("--data"), failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "history", "fetch 26.3", "extract 26.3", "normalize 26.3"})
    void dataOptionOnAnotherCommandIsAUsageError(String command) {
        String[] words = (command + " --data=x").split(" ");
        UsageException failure = assertThrows(UsageException.class, () -> reader.read(words));
        assertTrue(failure.getMessage().contains("only for the command publish"), failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "history", "fetch 26.3", "extract 26.3", "normalize 26.3"})
    void diffCommandOptionOnAnotherCommandIsAUsageError(String command) {
        String[] words = (command + " --diff-command=x").split(" ");
        UsageException failure = assertThrows(UsageException.class, () -> reader.read(words));
        assertTrue(failure.getMessage().contains("--diff-command is only for the command publish"), failure.getMessage());
    }

    @Test
    void publishWithoutADiffCommandHasNone() {
        assertEquals(Optional.empty(), reader.read(new String[] {"publish", "26.3", "--data=x"}).diffCommand());
    }

    @Test
    void diffCommandOptionIsResolvedAgainstTheCurrentDirectoryWithEqualsOrASpace() {
        Path expected = ENVIRONMENT.currentDirectory().resolve("LecternDiff/build/install/LecternDiff/bin/LecternDiff");
        String script = "../LecternPipeline/LecternDiff/build/install/LecternDiff/bin/LecternDiff";
        assertEquals(Optional.of(expected), reader.read(new String[] {"publish", "26.3", "--data=x", "--diff-command=" + script}).diffCommand());
        assertEquals(Optional.of(expected), reader.read(new String[] {"publish", "26.3", "--data=x", "--diff-command", script}).diffCommand());
    }

    @Test
    void diffCommandVariableIsUsedWhenTheOptionIsAbsentAndTheOptionWins() {
        PipelineConfigReader withVariable = new PipelineConfigReader(environment(false, Optional.of("/opt/diff/bin/LecternDiff")));
        Path variable = ENVIRONMENT.currentDirectory().resolve("/opt/diff/bin/LecternDiff").normalize();
        assertEquals(Optional.of(variable), withVariable.read(new String[] {"publish", "26.3", "--data=x"}).diffCommand());
        assertEquals(Optional.of(ENVIRONMENT.currentDirectory().resolve("mine")),
                withVariable.read(new String[] {"publish", "26.3", "--data=x", "--diff-command=mine"}).diffCommand());
    }

    @Test
    void diffCommandVariableIsIgnoredByTheCommandsThatDoNotDiff() {
        PipelineConfigReader withVariable = new PipelineConfigReader(environment(false, Optional.of("/opt/diff/bin/LecternDiff")));
        assertEquals(Optional.empty(), withVariable.read(new String[] {"normalize", "26.3"}).diffCommand());
        assertEquals(Optional.empty(), withVariable.read(new String[] {"list"}).diffCommand());
    }

    @Test
    void onWindowsAScriptWithoutAnExtensionGetsTheBatchExtension() {
        PipelineConfigReader windows = new PipelineConfigReader(environment(true, Optional.empty()));
        Path expected = ENVIRONMENT.currentDirectory().resolve("bin").resolve("LecternDiff.bat");
        assertEquals(Optional.of(expected), windows.read(new String[] {"publish", "26.3", "--data=x", "--diff-command=bin/LecternDiff"}).diffCommand());
    }

    @ParameterizedTest
    @ValueSource(strings = {"bin/LecternDiff.bat", "bin/LecternDiff.BAT", "bin/LecternDiff.cmd", "bin/LecternDiff.exe"})
    void onWindowsAScriptThatAlreadyHasAnExtensionIsKept(String script) {
        PipelineConfigReader windows = new PipelineConfigReader(environment(true, Optional.empty()));
        assertEquals(Optional.of(ENVIRONMENT.currentDirectory().resolve(script)),
                windows.read(new String[] {"publish", "26.3", "--data=x", "--diff-command=" + script}).diffCommand());
    }

    @Test
    void onOtherSystemsTheScriptIsKeptAsGiven() {
        assertEquals(Optional.of(ENVIRONMENT.currentDirectory().resolve("bin").resolve("LecternDiff")),
                reader.read(new String[] {"publish", "26.3", "--data=x", "--diff-command=bin/LecternDiff"}).diffCommand());
    }

    @Test
    void blankDiffCommandIsAUsageError() {
        assertThrows(UsageException.class, () -> reader.read(new String[] {"publish", "26.3", "--data=x", "--diff-command="}));
    }

    @ParameterizedTest
    @ValueSource(strings = {"--manifest-url", "--workbench", "--java", "--data", "--diff-command", "--history-start"})
    void optionWithoutAValueIsAUsageErrorSayingSo(String option) {
        UsageException failure = assertThrows(UsageException.class, () -> reader.read(new String[] {"publish", "26.3", option}));
        assertTrue(failure.getMessage().contains("needs a value"), failure.getMessage());
    }

    @Test
    void optionFollowedByAnotherOptionHasNoValue() {
        assertThrows(UsageException.class, () -> reader.read(new String[] {"publish", "26.3", "--data", "--workbench=x"}));
    }

    @Test
    void spacedFormWorksForEveryOption() {
        PipelineConfig config = reader.read(new String[] {"fetch", "26.3", "--workbench", "bench", "--java", "java", "--manifest-url", "http://127.0.0.1:8080/m"});
        assertEquals(ENVIRONMENT.currentDirectory().resolve("bench"), config.workbench());
        assertEquals(Path.of("java"), config.java());
        assertEquals(URI.create("http://127.0.0.1:8080/m"), config.manifestUrl());
    }

    @Test
    void historyTakesNoIdAndUsesTheDefaultStart() {
        PipelineConfig config = reader.read(new String[] {"history"});
        assertInstanceOf(Command.History.class, config.command());
        assertEquals(new VersionId("1.20.4"), config.historyStart());
    }

    @Test
    void defaultStartOfTheHistoryIs1204() {
        assertEquals("1.20.4", PipelineConfigReader.DEFAULT_HISTORY_START);
        assertEquals(new VersionId("1.20.4"), reader.read(new String[] {"list"}).historyStart());
        assertEquals(new VersionId("1.20.4"), reader.read(new String[] {"publish", "26.3", "--data=x"}).historyStart());
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "history", "fetch 26.3", "extract 26.3", "normalize 26.3", "publish 26.3 --data=x"})
    void startOfTheHistoryCanBeGivenToEveryCommandWithEqualsOrASpace(String command) {
        String[] withEquals = (command + " --history-start=1.21").split(" ");
        String[] withSpace = (command + " --history-start 1.21").split(" ");
        assertEquals(new VersionId("1.21"), reader.read(withEquals).historyStart());
        assertEquals(new VersionId("1.21"), reader.read(withSpace).historyStart());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a/b", " 1.21", "..", "back\\slash"})
    void startOfTheHistoryThatCannotBeAVersionIdIsAUsageError(String value) {
        UsageException failure = assertThrows(UsageException.class, () -> reader.read(new String[] {"history", "--history-start=" + value}));
        assertTrue(failure.getMessage().contains("first version of the history"), failure.getMessage());
    }

    @Test
    void historyStartGivenTwiceIsAUsageError() {
        assertThrows(UsageException.class, () -> reader.read(new String[] {"history", "--history-start=1.21", "--history-start=1.20"}));
    }

    @Test
    void usageNamesTheHistoryCommandAndTheOption() {
        assertTrue(PipelineConfigReader.USAGE.contains("history"), PipelineConfigReader.USAGE);
        assertTrue(PipelineConfigReader.USAGE.contains("--history-start=<id>"), PipelineConfigReader.USAGE);
    }
}
