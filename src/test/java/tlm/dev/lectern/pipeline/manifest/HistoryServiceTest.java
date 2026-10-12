package tlm.dev.lectern.pipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.fixtures.ManifestFixtures;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.model.VersionId;

class HistoryServiceTest {

    private static final String MANIFEST = ManifestFixtures.manifestJson(
            ManifestFixtures.entryJson("99.1-snapshot-1", "snapshot", ManifestFixtures.hoursAfterOrigin(60)),
            ManifestFixtures.entryJson("1.20.5", "release", ManifestFixtures.hoursAfterOrigin(50)),
            ManifestFixtures.entryJson("1.20.5-pre1", "snapshot", ManifestFixtures.hoursAfterOrigin(40)),
            ManifestFixtures.entryJson("99w14a", "snapshot", ManifestFixtures.hoursAfterOrigin(35)),
            ManifestFixtures.entryJson("23w51a", "snapshot", ManifestFixtures.hoursAfterOrigin(30)),
            ManifestFixtures.entryJson("not an id", "old_beta", ManifestFixtures.hoursAfterOrigin(25)),
            ManifestFixtures.entryJson("1.20.4", "release", ManifestFixtures.hoursAfterOrigin(20)),
            ManifestFixtures.entryJson("1.20.4-rc1", "snapshot", ManifestFixtures.hoursAfterOrigin(15)),
            ManifestFixtures.entryJson("1.20.3", "release", ManifestFixtures.hoursAfterOrigin(10)));

    private static HistoryService serviceStartingAt(Optional<String> start) {
        return new HistoryService(
                () -> MANIFEST,
                new ManifestReader(new JsonTextReader()),
                new VersionScope(Set.of(new VersionId("99w14a")), start.map(VersionId::new)));
    }

    private static List<String> ids(History history) {
        return history.versions().stream().map(listed -> listed.version().id().value()).toList();
    }

    private static Optional<VersionId> id(String value) {
        return value.equals("-") ? Optional.empty() : Optional.of(new VersionId(value));
    }

    @Test
    void historyStartsAtTheGivenVersionAndGoesFromTheOldestToTheNewest() {
        History history = serviceStartingAt(Optional.of("1.20.4")).history();
        assertEquals(List.of("1.20.4", "23w51a", "1.20.5-pre1", "1.20.5", "99.1-snapshot-1"), ids(history));
    }

    @Test
    void versionsBeforeTheStartAreLeftOutEvenWhenTheyShareTheLine() {
        List<String> ids = ids(serviceStartingAt(Optional.of("1.20.4")).history());
        assertFalse(ids.contains("1.20.3"));
        assertFalse(ids.contains("1.20.4-rc1"));
    }

    @Test
    void withoutAStartTheHistoryHoldsEveryVersionInScope() {
        assertEquals(List.of("1.20.3", "1.20.4-rc1", "1.20.4", "23w51a", "1.20.5-pre1", "1.20.5", "99.1-snapshot-1"),
                ids(serviceStartingAt(Optional.empty()).history()));
    }

    @Test
    void basesOfTheFirstVersionsFallOutsideTheHistoryAndAreNone() {
        History history = serviceStartingAt(Optional.of("1.20.4")).history();
        List<DiffBase> bases = history.versions().stream().map(ListedVersion::base).toList();
        assertEquals(new DiffBase(id("-"), id("-")), bases.get(0));
        assertEquals(new DiffBase(id("1.20.4"), id("-")), bases.get(1));
        assertEquals(new DiffBase(id("23w51a"), id("-")), bases.get(2));
        assertEquals(new DiffBase(id("1.20.5-pre1"), id("1.20.4")), bases.get(3));
        assertEquals(new DiffBase(id("1.20.5"), id("-")), bases.get(4));
    }

    @Test
    void totalsCountTheNamingsTheKindsAndTheBases() {
        HistoryTotals totals = serviceStartingAt(Optional.of("1.20.4")).history().totals();
        assertEquals(new HistoryTotals(5, 4, 1, 2, 0, 1, 2, 4, 1), totals);
    }

    @Test
    void emptyHistoryHasZeroTotals() {
        assertEquals(new HistoryTotals(0, 0, 0, 0, 0, 0, 0, 0, 0), new History(List.of()).totals());
    }

    @Test
    void startMissingFromTheManifestIsReported() {
        HistoryStartNotFoundException failure = assertThrows(HistoryStartNotFoundException.class, () -> serviceStartingAt(Optional.of("1.19.4")).history());
        assertTrue(failure.getMessage().contains("'1.19.4'"));
    }

    @Test
    void excludedVersionCannotBeTheStart() {
        assertThrows(HistoryStartNotFoundException.class, () -> serviceStartingAt(Optional.of("99w14a")).history());
    }

    @Test
    void fetchFailureReachesTheCaller() {
        HistoryService failing = new HistoryService(() -> {
            throw new ManifestFetchException("down");
        }, new ManifestReader(new JsonTextReader()), new VersionScope(Set.of()));
        assertThrows(ManifestFetchException.class, failing::history);
    }
}
