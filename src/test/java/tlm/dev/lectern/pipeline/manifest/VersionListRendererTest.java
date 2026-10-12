package tlm.dev.lectern.pipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.fixtures.ManifestFixtures;
import tlm.dev.lectern.pipeline.model.VersionId;

class VersionListRendererTest {

    private final VersionListRenderer renderer = new VersionListRenderer();

    private static ListedVersion listed(String id, long hours, String step, String release) {
        return new ListedVersion(
                ManifestFixtures.tracked(id, hours),
                new DiffBase(step.isEmpty() ? Optional.empty() : Optional.of(new VersionId(step)),
                        release.isEmpty() ? Optional.empty() : Optional.of(new VersionId(release))));
    }

    @Test
    void columnsAreAlignedAndAbsentBasesShowADash() {
        String text = renderer.render(List.of(
                listed("99.2-snapshot-1", 25, "99.1", ""),
                listed("99.1", 1, "99.1-rc-1", "98.9")));
        assertEquals("""
                ID               KIND      RELEASED              BASE       RELEASE BASE
                99.2-snapshot-1  snapshot  2090-01-02T01:00:00Z  99.1       -
                99.1             release   2090-01-01T01:00:00Z  99.1-rc-1  98.9
                """, text);
    }

    @Test
    void emptyListStillHasTheHeader() {
        assertEquals("ID  KIND  RELEASED  BASE  RELEASE BASE\n", renderer.render(List.of()));
    }

    @Test
    void outputUsesLineFeedsOnlyEndsWithOneAndHasNoTrailingSpaces() {
        String text = renderer.render(List.of(listed("99.1", 1, "", ""), listed("99.1-rc-1", 0, "", "")));
        assertFalse(text.contains("\r"));
        assertTrue(text.endsWith("\n"));
        assertFalse(text.endsWith("\n\n"));
        text.lines().forEach(line -> assertEquals(line.stripTrailing(), line));
    }

    @Test
    void sameInputGivesTheSameBytes() {
        List<ListedVersion> versions = List.of(listed("99.1", 1, "99.1-rc-1", "98.9"));
        assertEquals(renderer.render(versions), renderer.render(versions));
    }
}
