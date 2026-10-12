package tlm.dev.lectern.pipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.fixtures.ManifestFixtures;
import tlm.dev.lectern.pipeline.model.VersionId;

class HistoryRendererTest {

    private static ListedVersion listed(String id, long hours, String step, String release) {
        return new ListedVersion(
                ManifestFixtures.tracked(id, hours),
                new DiffBase(step.isEmpty() ? Optional.empty() : Optional.of(new VersionId(step)),
                        release.isEmpty() ? Optional.empty() : Optional.of(new VersionId(release))));
    }

    @Test
    void tableThenTotalsWithAlignedLabelsAndRightAlignedCounts() {
        History history = new History(List.of(
                listed("1.20.4", 1, "", ""),
                listed("23w51a", 2, "1.20.4", ""),
                listed("1.20.5", 3, "23w51a", "1.20.4"),
                listed("99.1-snapshot-1", 4, "1.20.5", "")));
        assertEquals("""
                ID               KIND      RELEASED              BASE    RELEASE BASE
                1.20.4           release   2090-01-01T01:00:00Z  -       -
                23w51a           snapshot  2090-01-01T02:00:00Z  1.20.4  -
                1.20.5           release   2090-01-01T03:00:00Z  23w51a  1.20.4
                99.1-snapshot-1  snapshot  2090-01-01T04:00:00Z  1.20.5  -

                totals
                  versions             4
                  old naming           3
                  new naming           1
                  release              2
                  release-candidate    0
                  pre-release          0
                  snapshot             2
                  with a step base     3
                  with a release base  1
                """, new HistoryRenderer().render(history));
    }

    @Test
    void emptyHistoryStillHasTheHeaderAndTheTotals() {
        String text = new HistoryRenderer().render(new History(List.of()));
        assertTrue(text.startsWith("ID  KIND  RELEASED  BASE  RELEASE BASE\n\ntotals\n"));
        assertTrue(text.contains("  versions             0\n"));
    }

    @Test
    void countsWiderThanOneDigitAreRightAligned() {
        List<ListedVersion> many = new java.util.ArrayList<>();
        for (int index = 0; index < 12; index++) {
            many.add(listed("99." + index + "-snapshot-1", index + 1, "", ""));
        }
        String text = new HistoryRenderer().render(new History(many));
        assertTrue(text.contains("  versions             12\n"), text);
        assertTrue(text.contains("  release               0\n"), text);
    }
}
