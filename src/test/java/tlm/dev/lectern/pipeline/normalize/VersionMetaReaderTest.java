package tlm.dev.lectern.pipeline.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlm.dev.lectern.pipeline.json.CanonicalJsonWriter;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.json.JsonValue;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.model.VersionKind;

class VersionMetaReaderTest {

    private static final VersionId ID = new VersionId("99.1");
    private static final String SHA1 = "0123456789abcdef0123456789abcdef01234567";

    @TempDir
    Path temporary;

    private final VersionMetaReader reader = new VersionMetaReader(new JsonTextReader());

    private Path sheet(String text) throws IOException {
        Path file = temporary.resolve("meta.json");
        Files.writeString(file, text);
        return file;
    }

    private static String sheetText(String id, String kind, String time, String stepBase, String releaseBase, String sha1, String extra) {
        return "{\"schema_version\": 1, \"id\": " + id + ", \"kind\": " + kind + ", \"release_time\": " + time + ", \"step_base\": " + stepBase
                + ", \"release_base\": " + releaseBase + ", \"jar_sha1\": " + sha1 + extra + "}";
    }

    private static String valid(String stepBase, String releaseBase, String extra) {
        return sheetText("\"99.1\"", "\"release\"", "\"2090-01-05T04:00:00Z\"", stepBase, releaseBase, "\"" + SHA1 + "\"", extra);
    }

    private NormalizationException refusal(String text) throws IOException {
        Path file = sheet(text);
        return assertThrows(NormalizationException.class, () -> reader.read(ID, file));
    }

    @Test
    void sheetWrittenByTheWriterIsReadBackToTheSameValues() throws IOException {
        Map<String, JsonValue> game = new LinkedHashMap<>();
        game.put("id", new JsonValue.StringValue("99.1"));
        game.put("world_version", new JsonValue.NumberValue("4000"));
        VersionMeta meta = new VersionMeta(ID, new VersionKind.Release(), Instant.parse("2090-01-05T04:00:00Z"),
                Optional.of(new VersionId("99.1-rc-1")), Optional.of(new VersionId("98.9")), new Sha1(SHA1), Optional.of(new JsonValue.ObjectValue(game)));
        Path file = temporary.resolve("written.json");
        Files.write(file, new VersionMetaWriter(new CanonicalJsonWriter()).toBytes(meta));
        assertEquals(meta, reader.read(ID, file));
    }

    @Test
    void nullBasesAndAMissingGameAreReadAsAbsent() throws IOException {
        VersionMeta meta = reader.read(ID, sheet(valid("null", "null", "")));
        assertEquals(Optional.empty(), meta.stepBase());
        assertEquals(Optional.empty(), meta.releaseBase());
        assertEquals(Optional.empty(), meta.game());
        assertEquals(new VersionKind.Release(), meta.kind());
        assertEquals(Instant.parse("2090-01-05T04:00:00Z"), meta.releaseTime());
    }

    @Test
    void basesAreReadAsVersionIds() throws IOException {
        VersionMeta meta = reader.read(ID, sheet(valid("\"99.1-rc-1\"", "\"98.9\"", "")));
        assertEquals(Optional.of(new VersionId("99.1-rc-1")), meta.stepBase());
        assertEquals(Optional.of(new VersionId("98.9")), meta.releaseBase());
    }

    @Test
    void schemaVersionOtherThanTheOneReadIsRefused() throws IOException {
        NormalizationException failure = refusal(valid("null", "null", "").replace("\"schema_version\": 1", "\"schema_version\": 2"));
        assertTrue(failure.getMessage().contains("schema_version is 2"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Version 99.1"), failure.getMessage());
    }

    @Test
    void missingSchemaVersionIsRefused() throws IOException {
        NormalizationException failure = refusal(valid("null", "null", "").replace("\"schema_version\": 1, ", ""));
        assertTrue(failure.getMessage().contains("schema_version is none"), failure.getMessage());
    }

    @Test
    void sheetOfAnotherVersionIsRefused() throws IOException {
        NormalizationException failure = refusal(valid("null", "null", "").replace("\"id\": \"99.1\"", "\"id\": \"99.2\""));
        assertTrue(failure.getMessage().contains("its id is '99.2'"), failure.getMessage());
    }

    @Test
    void unknownKindIsRefused() throws IOException {
        NormalizationException failure = refusal(valid("null", "null", "").replace("\"release\"", "\"stable\""));
        assertTrue(failure.getMessage().contains("kind 'stable'"), failure.getMessage());
    }

    @Test
    void releaseTimeThatIsNotAnInstantIsRefused() throws IOException {
        NormalizationException failure = refusal(valid("null", "null", "").replace("2090-01-05T04:00:00Z", "yesterday"));
        assertTrue(failure.getMessage().contains("release_time 'yesterday'"), failure.getMessage());
    }

    @Test
    void jarSha1ThatIsNotAHashIsRefused() throws IOException {
        NormalizationException failure = refusal(valid("null", "null", "").replace(SHA1, "XYZ"));
        assertTrue(failure.getMessage().contains("jar_sha1 is not a SHA-1"), failure.getMessage());
    }

    @Test
    void missingKeyIsNamed() throws IOException {
        NormalizationException failure = refusal(valid("null", "null", "").replace(", \"jar_sha1\": \"" + SHA1 + "\"", ""));
        assertTrue(failure.getMessage().contains("it has no jar_sha1"), failure.getMessage());
    }

    @Test
    void missingBaseKeyIsNamedEvenThoughItMayBeNull() throws IOException {
        NormalizationException failure = refusal(valid("null", "null", "").replace(", \"step_base\": null", ""));
        assertTrue(failure.getMessage().contains("it has no step_base"), failure.getMessage());
    }

    @Test
    void baseThatIsNeitherAStringNorNullIsRefused() throws IOException {
        NormalizationException failure = refusal(valid("12", "null", ""));
        assertTrue(failure.getMessage().contains("step_base is neither a string nor null"), failure.getMessage());
    }

    @Test
    void baseThatIsNotAVersionIdIsRefused() throws IOException {
        NormalizationException failure = refusal(valid("\"a/b\"", "null", ""));
        assertTrue(failure.getMessage().contains("step_base is not a version id"), failure.getMessage());
    }

    @Test
    void gameThatIsNotAnObjectIsRefused() throws IOException {
        NormalizationException failure = refusal(valid("null", "null", ", \"game\": \"x\""));
        assertTrue(failure.getMessage().contains("game is not an object"), failure.getMessage());
    }

    @Test
    void valueOfTheWrongTypeIsRefused() throws IOException {
        NormalizationException failure = refusal(valid("null", "null", "").replace("\"kind\": \"release\"", "\"kind\": 3"));
        assertTrue(failure.getMessage().contains("kind is not a string"), failure.getMessage());
    }

    @Test
    void textThatIsNotAnObjectIsRefused() throws IOException {
        NormalizationException failure = refusal("[]");
        assertTrue(failure.getMessage().contains("not a JSON object"), failure.getMessage());
    }

    @Test
    void textThatIsNotJsonIsRefusedWithTheFile() throws IOException {
        NormalizationException failure = refusal("{not json");
        assertTrue(failure.getMessage().contains("meta.json"), failure.getMessage());
    }

    @Test
    void missingFileIsReportedWithItsPath() {
        NormalizationException failure = assertThrows(NormalizationException.class, () -> reader.read(ID, temporary.resolve("absent.json")));
        assertTrue(failure.getMessage().contains("absent.json"), failure.getMessage());
    }
}
