package tlm.dev.lectern.pipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.fixtures.ManifestFixtures;
import tlm.dev.lectern.pipeline.model.VersionId;

class NewVersionPlannerTest {

    private final NewVersionPlanner planner = new NewVersionPlanner();

    private final List<TrackedVersion> tracked = List.of(
            ManifestFixtures.tracked("99.2-snapshot-1", 30),
            ManifestFixtures.tracked("99.1", 20),
            ManifestFixtures.tracked("99.1-rc-1", 10),
            ManifestFixtures.tracked("99.1-snapshot-1", 1));

    private static List<String> ids(List<TrackedVersion> versions) {
        return versions.stream().map(version -> version.id().value()).toList();
    }

    @Test
    void everyVersionIsNewWhenNothingIsKnown() {
        assertEquals(List.of("99.1-snapshot-1", "99.1-rc-1", "99.1", "99.2-snapshot-1"), ids(planner.plan(tracked, Set.of())));
    }

    @Test
    void knownVersionsAreLeftOutAndTheRestComesOldestFirst() {
        Set<VersionId> known = Set.of(new VersionId("99.1-snapshot-1"), new VersionId("99.1-rc-1"));
        assertEquals(List.of("99.1", "99.2-snapshot-1"), ids(planner.plan(tracked, known)));
    }

    @Test
    void aGapInTheKnownVersionsIsStillReportedInChronologicalOrder() {
        Set<VersionId> known = Set.of(new VersionId("99.1-snapshot-1"), new VersionId("99.1"));
        assertEquals(List.of("99.1-rc-1", "99.2-snapshot-1"), ids(planner.plan(tracked, known)));
    }

    @Test
    void nothingIsNewWhenEverythingIsKnown() {
        Set<VersionId> known = Set.of(
                new VersionId("99.1-snapshot-1"), new VersionId("99.1-rc-1"), new VersionId("99.1"), new VersionId("99.2-snapshot-1"));
        assertEquals(List.of(), planner.plan(tracked, known));
    }

    @Test
    void knownIdsAbsentFromTheManifestAreIgnored() {
        Set<VersionId> known = Set.of(new VersionId("98.9"));
        assertEquals(4, planner.plan(tracked, known).size());
    }

    @Test
    void planDoesNotDependOnTheOrderOfItsInput() {
        List<TrackedVersion> reversed = tracked.reversed();
        assertEquals(planner.plan(tracked, Set.of()), planner.plan(reversed, Set.of()));
    }
}
