package tlm.dev.lectern.pipeline.normalize;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.json.CanonicalJsonWriter;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.json.JsonValue;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.model.VersionKind;

class VersionMetaWriterTest {

    private static final Sha1 JAR_SHA1 = new Sha1("0123456789abcdef0123456789abcdef01234567");

    private final VersionMetaWriter writer = new VersionMetaWriter(new CanonicalJsonWriter());

    private static JsonValue.ObjectValue game() {
        Map<String, JsonValue> members = new LinkedHashMap<>();
        members.put("stable", new JsonValue.BooleanValue(true));
        members.put("id", new JsonValue.StringValue("99.1"));
        members.put("world_version", new JsonValue.NumberValue("5023"));
        return new JsonValue.ObjectValue(members);
    }

    private static VersionMeta meta(Optional<VersionId> step, Optional<VersionId> release, Optional<JsonValue.ObjectValue> game) {
        return new VersionMeta(new VersionId("99.1"), new VersionKind.Release(), Instant.parse("2090-01-02T03:04:05Z"), step, release, JAR_SHA1, game);
    }

    @Test
    void fullMetaIsWrittenWithSortedKeysAndEveryField() {
        VersionMeta meta = meta(Optional.of(new VersionId("99.1-rc-1")), Optional.of(new VersionId("98.9")), Optional.of(game()));
        assertEquals("""
                {
                  "game": {
                    "id": "99.1",
                    "stable": true,
                    "world_version": 5023
                  },
                  "id": "99.1",
                  "jar_sha1": "0123456789abcdef0123456789abcdef01234567",
                  "kind": "release",
                  "release_base": "98.9",
                  "release_time": "2090-01-02T03:04:05Z",
                  "schema_version": 1,
                  "step_base": "99.1-rc-1"
                }
                """, new String(writer.toBytes(meta), StandardCharsets.UTF_8));
    }

    @Test
    void missingBasesAreWrittenAsNullAndMissingGameIsAbsent() {
        String text = new String(writer.toBytes(meta(Optional.empty(), Optional.empty(), Optional.empty())), StandardCharsets.UTF_8);
        assertTrue(text.contains("\"release_base\": null"), text);
        assertTrue(text.contains("\"step_base\": null"), text);
        assertFalse(text.contains("\"game\""), text);
    }

    @Test
    void kindIsTheSharedLabel() {
        for (VersionKind kind : new VersionKind[] {new VersionKind.Release(), new VersionKind.ReleaseCandidate(), new VersionKind.PreRelease(), new VersionKind.Snapshot()}) {
            VersionMeta meta = new VersionMeta(new VersionId("99.1"), kind, Instant.parse("2090-01-02T03:04:05Z"),
                    Optional.empty(), Optional.empty(), JAR_SHA1, Optional.empty());
            assertTrue(new String(writer.toBytes(meta), StandardCharsets.UTF_8).contains("\"kind\": \"" + kind.label() + "\""), kind.label());
        }
    }

    @Test
    void releaseTimeIsUtcWithAZoneLetterAndSeconds() {
        VersionMeta meta = new VersionMeta(new VersionId("99.1"), new VersionKind.Snapshot(), Instant.parse("2090-12-31T23:59:59Z"),
                Optional.empty(), Optional.empty(), JAR_SHA1, Optional.empty());
        assertTrue(new String(writer.toBytes(meta), StandardCharsets.UTF_8).contains("\"release_time\": \"2090-12-31T23:59:59Z\""));
    }

    @Test
    void sameMetaGivesTheSameBytesAndTheBytesAreCanonical() {
        VersionMeta meta = meta(Optional.of(new VersionId("99.1-rc-1")), Optional.empty(), Optional.of(game()));
        byte[] first = writer.toBytes(meta);
        assertArrayEquals(first, writer.toBytes(meta));
        JsonTextReader reader = new JsonTextReader();
        assertArrayEquals(first, new CanonicalJsonWriter().toBytes(reader.read(new String(first, StandardCharsets.UTF_8))));
    }

    @Test
    void schemaVersionIsOne() {
        assertEquals(1, VersionMetaWriter.SCHEMA_VERSION);
    }
}
