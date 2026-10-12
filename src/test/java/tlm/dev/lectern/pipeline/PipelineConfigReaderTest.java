package tlm.dev.lectern.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tlm.dev.lectern.pipeline.model.VersionId;

class PipelineConfigReaderTest {

    private static final Path BASE = Path.of("lectern-test-base").toAbsolutePath();
    private static final Environment ENVIRONMENT = new Environment(BASE.resolve("LecternPipeline"), BASE.resolve("jdk"), BASE.resolve("tmp"));

    private final PipelineConfigReader reader = new PipelineConfigReader(ENVIRONMENT);

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
    @ValueSource(strings = {"fetch", "extract"})
    void commandWithoutAVersionIdIsAUsageError(String command) {
        UsageException failure = assertThrows(UsageException.class, () -> reader.read(new String[] {command}));
        assertTrue(failure.getMessage().contains("needs a version id"), failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"fetch", "extract"})
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
    @ValueSource(strings = {"--limit=3", "extra", "--manifest-url", "-manifest-url=http://127.0.0.1/x", "--workbench", "--java"})
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
}
