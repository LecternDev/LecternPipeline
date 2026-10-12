package tlm.dev.lectern.pipeline.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StagedSetTest {

    @Test
    void statusAndPathPairsAreReadFromNulSeparatedOutput() {
        StagedSet staged = StagedSet.parse("A\0reports/a.json\0M\0data/b.json\0D\0data/c.json\0");
        assertEquals(Map.of("reports/a.json", "A", "data/b.json", "M", "data/c.json", "D"), staged.statuses());
    }

    @Test
    void emptyOutputIsAnEmptySet() {
        assertEquals(Map.of(), StagedSet.parse("").statuses());
    }

    @Test
    void pathsWithSpacesAndNonAsciiCharactersAreKeptWhole() {
        String path = "reports/with space/caf" + (char) 233 + ".json";
        StagedSet staged = StagedSet.parse("A\0" + path + "\0");
        assertEquals(Map.of(path, "A"), staged.statuses());
    }

    @Test
    void statusWithoutAPathIsAnError() {
        PublicationException failure = assertThrows(PublicationException.class, () -> StagedSet.parse("A\0reports/a.json\0M\0"));
        assertTrue(failure.getMessage().contains("'M'"), failure.getMessage());
    }

    @Test
    void countsFollowTheStatusAndThePrefix() {
        StagedSet staged = StagedSet.parse("A\0reports/a.json\0A\0reports/b.json\0A\0data/c.json\0M\0reports/d.json\0A\0versions/1/meta.json\0");
        assertEquals(2, staged.count("A", "reports/"));
        assertEquals(1, staged.count("A", "data/"));
        assertEquals(1, staged.count("M", "reports/"));
        assertEquals(0, staged.count("D", "reports/"));
    }
}
