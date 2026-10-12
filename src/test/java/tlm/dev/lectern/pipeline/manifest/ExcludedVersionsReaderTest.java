package tlm.dev.lectern.pipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.model.VersionId;

class ExcludedVersionsReaderTest {

    private final ExcludedVersionsReader reader = new ExcludedVersionsReader();

    @Test
    void oneIdPerLineIsRead() {
        assertEquals(Set.of(new VersionId("99w01a"), new VersionId("99.1")), reader.parse("99w01a\n99.1\n"));
    }

    @Test
    void blankLinesSurroundingSpacesAndWindowsLineEndingsAreTolerated() {
        assertEquals(Set.of(new VersionId("99w01a"), new VersionId("99.1")), reader.parse("\r\n  99w01a  \r\n\r\n99.1\r\n\r\n"));
    }

    @Test
    void emptyTextExcludesNothing() {
        assertEquals(Set.of(), reader.parse(""));
    }

    @Test
    void idListedTwiceIsRejectedWithItsLine() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> reader.parse("99w01a\n99.1\n99w01a\n"));
        assertTrue(failure.getMessage().contains("line 3"), failure.getMessage());
    }

    @Test
    void idWithAPathSeparatorIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> reader.parse("99/1\n"));
    }

    @Test
    void shippedListContainsOnlyAsciiIdsWithAnExcludableShape() throws IOException {
        try (InputStream stream = ExcludedVersionsReaderTest.class.getResourceAsStream("/excluded-versions.txt")) {
            assertNotNull(stream, "excluded-versions.txt must be on the classpath");
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.chars().allMatch(character -> character < 128), "the list must be ASCII");
            Set<VersionId> excluded = reader.parse(text);
            assertTrue(excluded.contains(new VersionId("26w14a")));
            assertTrue(excluded.contains(new VersionId("15w14a")));
            for (VersionId id : excluded) {
                assertTrue(VersionFormParser.parse(id).isPresent(), id.value() + " has no normal shape, excluding it has no effect");
            }
        }
    }
}
