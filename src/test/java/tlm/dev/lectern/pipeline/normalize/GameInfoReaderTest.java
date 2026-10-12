package tlm.dev.lectern.pipeline.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlm.dev.lectern.pipeline.fixtures.FetchFixtures;
import tlm.dev.lectern.pipeline.fixtures.JarFixtures;
import tlm.dev.lectern.pipeline.json.CanonicalJsonWriter;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.json.JsonValue;
import tlm.dev.lectern.pipeline.model.VersionId;

class GameInfoReaderTest {

    private static final VersionId ID = new VersionId("99.1");

    private final GameInfoReader reader = new GameInfoReader(new JsonTextReader());

    @TempDir
    Path directory;

    private Path jarWithVersionJson(String text) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/MANIFEST.MF", JarFixtures.text("Manifest-Version: 1.0\n\n"));
        entries.put("version.json", JarFixtures.text(text));
        return JarFixtures.write(directory.resolve("server.jar"), entries);
    }

    private String canonical(Optional<JsonValue.ObjectValue> game) {
        return new CanonicalJsonWriter().write(game.orElseThrow());
    }

    @Test
    void theSixKeysAreCopiedAsTheyAreAndOtherKeysAreDropped() {
        Path jar = jarWithVersionJson("{\"id\": \"99.1\", \"name\": \"Invented\", \"world_version\": 5023, \"series_id\": \"main\", \"protocol_version\": 777, "
                + "\"pack_version\": {\"resource_major\": 97, \"resource_minor\": 1, \"data_major\": 121, \"data_minor\": 0}, "
                + "\"build_time\": \"2090-01-02T03:04:05+00:00\", \"java_version\": 25, \"stable\": true, \"use_editor\": false}");
        assertEquals("""
                {
                  "build_time": "2090-01-02T03:04:05+00:00",
                  "id": "99.1",
                  "pack_version": {
                    "data_major": 121,
                    "data_minor": 0,
                    "resource_major": 97,
                    "resource_minor": 1
                  },
                  "protocol_version": 777,
                  "stable": true,
                  "world_version": 5023
                }
                """, canonical(reader.read(ID, jar)));
    }

    @Test
    void packVersionAsAPlainNumberIsKeptAsANumber() {
        Path jar = jarWithVersionJson("{\"id\": \"99.0\", \"world_version\": 1976, \"protocol_version\": 498, \"pack_version\": 4, \"stable\": true, \"build_time\": \"2090-01-01T00:00:00+00:00\"}");
        assertTrue(canonical(reader.read(ID, jar)).contains("\"pack_version\": 4,"));
    }

    @Test
    void keyAbsentFromTheJarIsAbsentFromTheResult() {
        Path jar = jarWithVersionJson("{\"id\": \"99.0\", \"world_version\": 1}");
        assertEquals(List.of("id", "world_version"), List.copyOf(reader.read(ID, jar).orElseThrow().members().keySet()));
    }

    @Test
    void jarWithoutVersionJsonGivesNoGameInformation() {
        Path jar = JarFixtures.jarWithManifest(directory.resolve("server.jar"), "Manifest-Version: 1.0\n\n", false);
        assertEquals(Optional.empty(), reader.read(ID, jar));
    }

    @Test
    void numbersKeepTheirWrittenText() {
        Path jar = jarWithVersionJson("{\"world_version\": 5023.0}");
        assertTrue(canonical(reader.read(ID, jar)).contains("5023.0"));
    }

    @Test
    void versionJsonThatIsNotAnObjectIsRejectedNamingTheVersion() {
        Path jar = jarWithVersionJson("[1, 2]");
        NormalizationException failure = assertThrows(NormalizationException.class, () -> reader.read(ID, jar));
        assertTrue(failure.getMessage().contains("Version 99.1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("JSON object"), failure.getMessage());
    }

    @Test
    void versionJsonThatIsNotJsonIsRejected() {
        Path jar = jarWithVersionJson("not json at all");
        NormalizationException failure = assertThrows(NormalizationException.class, () -> reader.read(ID, jar));
        assertTrue(failure.getMessage().contains("not valid JSON"), failure.getMessage());
    }

    @Test
    void versionJsonThatIsNotUtf8IsRejected() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("version.json", new byte[] {'{', '"', 'i', 'd', '"', ':', '"', (byte) 0xFF, (byte) 0xFE, '"', '}'});
        Path jar = JarFixtures.write(directory.resolve("server.jar"), entries);
        assertThrows(NormalizationException.class, () -> reader.read(ID, jar));
    }

    @Test
    void oversizedVersionJsonIsRejectedWithoutReadingItAll() {
        Path jar = jarWithVersionJson("{\"id\": \"" + "a".repeat(2 * 1024 * 1024) + "\"}");
        NormalizationException failure = assertThrows(NormalizationException.class, () -> reader.read(ID, jar));
        assertTrue(failure.getMessage().contains("larger than"), failure.getMessage());
    }

    @Test
    void fileThatIsNotAZipArchiveIsRejected() throws IOException {
        Path file = directory.resolve("server.jar");
        Files.write(file, FetchFixtures.inventedBytes(3, 2000));
        NormalizationException failure = assertThrows(NormalizationException.class, () -> reader.read(ID, file));
        assertTrue(failure.getMessage().contains("Version 99.1"), failure.getMessage());
    }

    @Test
    void readingExtractsNothingToDisk() throws IOException {
        Path jar = jarWithVersionJson("{\"id\": \"99.1\"}");
        reader.read(ID, jar);
        try (var files = Files.list(directory)) {
            assertEquals(List.of("server.jar"), files.map(path -> path.getFileName().toString()).toList());
        }
    }
}
