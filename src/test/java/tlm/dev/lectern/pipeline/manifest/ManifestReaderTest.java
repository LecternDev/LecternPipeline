package tlm.dev.lectern.pipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tlm.dev.lectern.pipeline.fixtures.ManifestFixtures;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;

class ManifestReaderTest {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";

    private final ManifestReader reader = new ManifestReader(new JsonTextReader());

    private static String entryWith(String id, String type, String url, String releaseTime, String sha1) {
        return "{\"id\": " + id + ", \"type\": " + type + ", \"url\": " + url
                + ", \"releaseTime\": " + releaseTime + ", \"sha1\": " + sha1 + "}";
    }

    private static String validEntry() {
        return entryWith("\"99.1\"", "\"release\"", "\"https://manifest.example.invalid/a.json\"",
                "\"2090-01-02T03:04:05+00:00\"", "\"" + VALID_SHA1 + "\"");
    }

    @Test
    void entryIsReadWithItsFiveMembers() {
        List<ManifestEntry> entries = reader.read(ManifestFixtures.manifestJson(validEntry()));
        assertEquals(
                List.of(new ManifestEntry(
                        new VersionId("99.1"),
                        "release",
                        URI.create("https://manifest.example.invalid/a.json"),
                        Instant.parse("2090-01-02T03:04:05Z"),
                        new Sha1(VALID_SHA1))),
                entries);
    }

    @Test
    void membersOutsideTheFiveAreIgnoredAndOrderIsKept() {
        String first = ManifestFixtures.entryJson("99.2", "release", Instant.parse("2090-02-01T00:00:00Z"));
        String second = ManifestFixtures.entryJson("99.1", "release", Instant.parse("2090-01-01T00:00:00Z"));
        List<String> ids = reader.read(ManifestFixtures.manifestJson(first, second)).stream()
                .map(entry -> entry.id().value())
                .toList();
        assertEquals(List.of("99.2", "99.1"), ids);
    }

    @Test
    void releaseTimeWithAZoneOffsetIsConvertedToAnInstant() {
        String entry = entryWith("\"99.1\"", "\"release\"", "\"https://manifest.example.invalid/a.json\"",
                "\"2090-01-02T05:04:05+02:00\"", "\"" + VALID_SHA1 + "\"");
        assertEquals(Instant.parse("2090-01-02T03:04:05Z"), reader.read(ManifestFixtures.manifestJson(entry)).get(0).releaseTime());
    }

    @Test
    void idWithSpacesIsKeptVerbatim() {
        String entry = ManifestFixtures.entryJson("99.1 Pre-Release 1", "snapshot", Instant.parse("2090-01-01T00:00:00Z"));
        assertEquals("99.1 Pre-Release 1", reader.read(ManifestFixtures.manifestJson(entry)).get(0).id().value());
    }

    @Test
    void emptyVersionListGivesNoEntry() {
        assertEquals(List.of(), reader.read(ManifestFixtures.manifestJson()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "[]",
            "{\"versions\": {}}",
            "{\"versions\": [1]}",
            "{\"versions\": [\"99.1\"]}",
            "not json",
            ""})
    void manifestWithoutAVersionArrayOfObjectsIsRejected(String text) {
        assertThrows(ManifestFormatException.class, () -> reader.read(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "type", "url", "releaseTime", "sha1"})
    void missingMemberIsRejectedWithTheEntryIndexAndTheMemberName(String member) {
        String full = validEntry();
        String without = full.replaceAll(", \"" + member + "\": (\"[^\"]*\")", "").replaceAll("\"" + member + "\": (\"[^\"]*\"), ", "");
        ManifestFormatException failure = assertThrows(ManifestFormatException.class, () -> reader.read(ManifestFixtures.manifestJson(validEntry(), without)));
        assertTrue(failure.getMessage().contains("entry 1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("'" + member + "'"), failure.getMessage());
    }

    @Test
    void memberOfTheWrongJsonTypeIsRejected() {
        String entry = entryWith("\"99.1\"", "3", "\"https://manifest.example.invalid/a.json\"",
                "\"2090-01-02T03:04:05+00:00\"", "\"" + VALID_SHA1 + "\"");
        ManifestFormatException failure = assertThrows(ManifestFormatException.class, () -> reader.read(ManifestFixtures.manifestJson(entry)));
        assertTrue(failure.getMessage().contains("'type'"), failure.getMessage());
    }

    @Test
    void invalidSha1IsRejectedWithTheVersionId() {
        String entry = entryWith("\"99.1\"", "\"release\"", "\"https://manifest.example.invalid/a.json\"",
                "\"2090-01-02T03:04:05+00:00\"", "\"NOT-A-HASH\"");
        ManifestFormatException failure = assertThrows(ManifestFormatException.class, () -> reader.read(ManifestFixtures.manifestJson(entry)));
        assertTrue(failure.getMessage().contains("99.1"), failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"relative/path.json\"", "\"not a url\"", "\"\""})
    void urlThatIsNotAbsoluteIsRejected(String url) {
        String entry = entryWith("\"99.1\"", "\"release\"", url, "\"2090-01-02T03:04:05+00:00\"", "\"" + VALID_SHA1 + "\"");
        assertThrows(ManifestFormatException.class, () -> reader.read(ManifestFixtures.manifestJson(entry)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"2090-01-02\"", "\"2090-01-02T03:04:05\"", "\"yesterday\"", "\"\""})
    void releaseTimeWithoutAZoneOffsetOrNotADateIsRejected(String releaseTime) {
        String entry = entryWith("\"99.1\"", "\"release\"", "\"https://manifest.example.invalid/a.json\"", releaseTime, "\"" + VALID_SHA1 + "\"");
        assertThrows(ManifestFormatException.class, () -> reader.read(ManifestFixtures.manifestJson(entry)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\" 99.1\"", "\"99/1\""})
    void invalidIdIsRejected(String id) {
        String entry = entryWith(id, "\"release\"", "\"https://manifest.example.invalid/a.json\"",
                "\"2090-01-02T03:04:05+00:00\"", "\"" + VALID_SHA1 + "\"");
        assertThrows(ManifestFormatException.class, () -> reader.read(ManifestFixtures.manifestJson(entry)));
    }

    @Test
    void repeatedIdIsRejectedWithBothPositionsKnown() {
        ManifestFormatException failure = assertThrows(ManifestFormatException.class,
                () -> reader.read(ManifestFixtures.manifestJson(validEntry(), validEntry())));
        assertTrue(failure.getMessage().contains("entry 1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("99.1"), failure.getMessage());
    }

    @Test
    void duplicatedMemberNameIsRejected() {
        String entry = "{\"id\": \"99.1\", \"id\": \"99.2\", \"type\": \"release\"}";
        assertThrows(ManifestFormatException.class, () -> reader.read(ManifestFixtures.manifestJson(entry)));
    }
}
