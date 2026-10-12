package tlm.dev.lectern.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PipelineConfigReaderTest {

    private final PipelineConfigReader reader = new PipelineConfigReader();

    @Test
    void listWithoutOptionUsesTheOfficialManifestUrl() {
        assertEquals(URI.create(PipelineConfigReader.DEFAULT_MANIFEST_URL), reader.read(new String[] {"list"}).manifestUrl());
    }

    @Test
    void defaultManifestUrlIsTheOfficialV2Manifest() {
        assertEquals("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json", PipelineConfigReader.DEFAULT_MANIFEST_URL);
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
    @ValueSource(strings = {"--limit=3", "extra", "--manifest-url", "-manifest-url=http://127.0.0.1/x"})
    void unknownArgumentAfterTheCommandIsAUsageError(String argument) {
        UsageException failure = assertThrows(UsageException.class, () -> reader.read(new String[] {"list", argument}));
        assertTrue(failure.getMessage().contains("Unknown argument"), failure.getMessage());
    }

    @Test
    void manifestUrlOptionGivenTwiceIsAUsageError() {
        String[] args = {"list", "--manifest-url=http://127.0.0.1/a", "--manifest-url=http://127.0.0.1/b"};
        assertThrows(UsageException.class, () -> reader.read(args));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not a url", "file:///etc/passwd", "ftp://127.0.0.1/manifest", "/relative/path", "http:///nohost", "mailto:someone@example.invalid"})
    void manifestUrlThatIsNotAWebUrlWithAHostIsAUsageError(String url) {
        assertThrows(UsageException.class, () -> reader.read(new String[] {"list", "--manifest-url=" + url}));
    }
}
