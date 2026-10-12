package tlm.dev.lectern.pipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.fixtures.ManifestFixtures;

class TrackedVersionTest {

    private static List<String> ids(List<TrackedVersion> versions) {
        return versions.stream().map(version -> version.id().value()).toList();
    }

    @Test
    void newestFirstOrdersByReleaseTimeDescending() {
        List<TrackedVersion> versions = new ArrayList<>(List.of(
                ManifestFixtures.tracked("99.1-snapshot-1", 1),
                ManifestFixtures.tracked("99.1-snapshot-2", 5),
                ManifestFixtures.tracked("99.1", 9),
                ManifestFixtures.tracked("99.2-snapshot-1", 7)));
        versions.sort(TrackedVersion.NEWEST_FIRST);
        assertEquals(List.of("99.1", "99.2-snapshot-1", "99.1-snapshot-2", "99.1-snapshot-1"), ids(versions));
    }

    @Test
    void orderFollowsTheReleaseTimeAndNeverTheIdText() {
        List<TrackedVersion> versions = new ArrayList<>(List.of(
                ManifestFixtures.tracked("99.9", 1),
                ManifestFixtures.tracked("99.10", 2)));
        versions.sort(TrackedVersion.NEWEST_FIRST);
        assertEquals(List.of("99.10", "99.9"), ids(versions));
    }

    @Test
    void equalReleaseTimesAreBrokenByTheIdSoTheOrderIsStable() {
        List<TrackedVersion> versions = List.of(
                ManifestFixtures.tracked("99.1-snapshot-2", 4),
                ManifestFixtures.tracked("99.1-snapshot-1", 4),
                ManifestFixtures.tracked("99.1-snapshot-3", 4));
        for (long seed = 0; seed < 20; seed++) {
            List<TrackedVersion> shuffled = new ArrayList<>(versions);
            Collections.shuffle(shuffled, new Random(seed));
            shuffled.sort(TrackedVersion.NEWEST_FIRST);
            assertEquals(List.of("99.1-snapshot-1", "99.1-snapshot-2", "99.1-snapshot-3"), ids(shuffled), "seed " + seed);
        }
    }
}
