package tlm.dev.lectern.pipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.fixtures.ManifestFixtures;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.model.VersionId;

class RecentVersionsServiceTest {

    private static final String MANIFEST = ManifestFixtures.manifestJson(
            ManifestFixtures.entryJson("99.2-snapshot-1", "snapshot", ManifestFixtures.hoursAfterOrigin(300)),
            ManifestFixtures.entryJson("99.1", "release", ManifestFixtures.hoursAfterOrigin(200)),
            ManifestFixtures.entryJson("99.1-rc-1", "snapshot", ManifestFixtures.hoursAfterOrigin(100)),
            ManifestFixtures.entryJson("99w14a", "snapshot", ManifestFixtures.hoursAfterOrigin(90)),
            ManifestFixtures.entryJson("not an id", "old_beta", ManifestFixtures.hoursAfterOrigin(80)),
            ManifestFixtures.entryJson("98.9", "release", ManifestFixtures.hoursAfterOrigin(10)));

    private final RecentVersionsService service = new RecentVersionsService(
            () -> MANIFEST,
            new ManifestReader(new JsonTextReader()),
            new VersionScope(Set.of(new VersionId("99w14a"))));

    @Test
    void newestVersionsComeFirstWithTheirDiffBase() {
        List<ListedVersion> listed = service.recent(3);
        assertEquals(List.of("99.2-snapshot-1", "99.1", "99.1-rc-1"), listed.stream().map(item -> item.version().id().value()).toList());
        assertEquals(new DiffBase(Optional.of(new VersionId("99.1")), Optional.empty()), listed.get(0).base());
        assertEquals(new DiffBase(Optional.of(new VersionId("99.1-rc-1")), Optional.of(new VersionId("98.9"))), listed.get(1).base());
        assertEquals(new DiffBase(Optional.of(new VersionId("98.9")), Optional.empty()), listed.get(2).base());
    }

    @Test
    void versionsOutsideTheScopeNeverAppear() {
        List<String> ids = service.recent(10).stream().map(item -> item.version().id().value()).toList();
        assertEquals(List.of("99.2-snapshot-1", "99.1", "99.1-rc-1", "98.9"), ids);
    }

    @Test
    void asksForMoreThanExistsGivesEverythingInScope() {
        assertEquals(4, service.recent(100).size());
    }

    @Test
    void countBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.recent(0));
    }

    @Test
    void fetchFailureReachesTheCaller() {
        RecentVersionsService failing = new RecentVersionsService(
                () -> {
                    throw new ManifestFetchException("down");
                },
                new ManifestReader(new JsonTextReader()),
                new VersionScope(Set.of()));
        assertThrows(ManifestFetchException.class, () -> failing.recent(1));
    }

    @Test
    void malformedManifestReachesTheCaller() {
        RecentVersionsService broken = new RecentVersionsService(
                () -> "{}",
                new ManifestReader(new JsonTextReader()),
                new VersionScope(Set.of()));
        assertThrows(ManifestFormatException.class, () -> broken.recent(1));
    }
}
