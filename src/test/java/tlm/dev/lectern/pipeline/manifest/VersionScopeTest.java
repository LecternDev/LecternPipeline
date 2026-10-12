package tlm.dev.lectern.pipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.fixtures.ManifestFixtures;
import tlm.dev.lectern.pipeline.model.VersionId;

class VersionScopeTest {

    private final VersionScope scope = new VersionScope(Set.of(new VersionId("99w14a")));

    @Test
    void excludedIdHasNoKindEvenWithANormalShape() {
        assertTrue(scope.kindOf(new VersionId("99w14a")).isEmpty());
        assertTrue(scope.formOf(new VersionId("99w14a")).isEmpty());
    }

    @Test
    void sameShapeThatIsNotExcludedHasItsKind() {
        assertEquals(Optional.of("snapshot"), scope.kindOf(new VersionId("99w15a")).map(kind -> kind.label()));
    }

    @Test
    void idOutsideTheFourFormsHasNoKind() {
        assertTrue(scope.kindOf(new VersionId("1.14 Pre-Release 1")).isEmpty());
    }

    @Test
    void trackKeepsOnlyScopedVersionsNewestFirst() {
        List<ManifestEntry> entries = List.of(
                ManifestFixtures.entry("99.1-snapshot-1", "snapshot", ManifestFixtures.hoursAfterOrigin(1)),
                ManifestFixtures.entry("99w14a", "snapshot", ManifestFixtures.hoursAfterOrigin(2)),
                ManifestFixtures.entry("99.1", "release", ManifestFixtures.hoursAfterOrigin(3)),
                ManifestFixtures.entry("b9.9.9", "old_beta", ManifestFixtures.hoursAfterOrigin(4)),
                ManifestFixtures.entry("99.1-rc-1", "snapshot", ManifestFixtures.hoursAfterOrigin(2)));
        List<String> ids = scope.track(entries).stream().map(version -> version.id().value()).toList();
        assertEquals(List.of("99.1", "99.1-rc-1", "99.1-snapshot-1"), ids);
    }

    @Test
    void kindComesFromTheIdAndNotFromTheManifestType() {
        List<ManifestEntry> entries = List.of(
                ManifestFixtures.entry("1.9", "snapshot", ManifestFixtures.hoursAfterOrigin(1)),
                ManifestFixtures.entry("99.2-pre-1", "release", ManifestFixtures.hoursAfterOrigin(2)));
        List<String> labels = scope.track(entries).stream().map(version -> version.kind().label()).toList();
        assertEquals(List.of("pre-release", "release"), labels);
    }
}
