package tlm.dev.lectern.pipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.fixtures.ManifestFixtures;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.model.VersionId;

class VersionLookupServiceTest {

    private static final String MANIFEST = ManifestFixtures.manifestJson(
            ManifestFixtures.entryJson("99.1", "release", ManifestFixtures.hoursAfterOrigin(200)),
            ManifestFixtures.entryJson("99.1-rc-1", "snapshot", ManifestFixtures.hoursAfterOrigin(100)),
            ManifestFixtures.entryJson("99w14a", "snapshot", ManifestFixtures.hoursAfterOrigin(90)),
            ManifestFixtures.entryJson("not an id", "old_beta", ManifestFixtures.hoursAfterOrigin(80)));

    private final VersionLookupService service = new VersionLookupService(
            () -> MANIFEST,
            new ManifestReader(new JsonTextReader()),
            new VersionScope(Set.of(new VersionId("99w14a"))));

    @Test
    void versionInScopeIsFoundWithItsEntryAndKind() {
        TrackedVersion version = service.find(new VersionId("99.1-rc-1"));
        assertEquals("99.1-rc-1", version.entry().id().value());
        assertEquals("release-candidate", version.kind().label());
        assertEquals(ManifestFixtures.sha1Of("99.1-rc-1"), version.entry().sha1().hex());
    }

    @Test
    void idAbsentFromTheManifestIsReported() {
        VersionNotFoundException failure = assertThrows(VersionNotFoundException.class, () -> service.find(new VersionId("98.9")));
        assertTrue(failure.getMessage().contains("98.9"), failure.getMessage());
        assertTrue(failure.getMessage().contains("not in the Mojang manifest"), failure.getMessage());
    }

    @Test
    void excludedVersionIsRefusedAndSaidSo() {
        VersionOutOfScopeException failure = assertThrows(VersionOutOfScopeException.class, () -> service.find(new VersionId("99w14a")));
        assertTrue(failure.getMessage().contains("exclusion list"), failure.getMessage());
        assertTrue(failure.getMessage().contains("99w14a"), failure.getMessage());
    }

    @Test
    void idWithoutANormalShapeIsRefusedAndSaidSo() {
        VersionOutOfScopeException failure = assertThrows(VersionOutOfScopeException.class, () -> service.find(new VersionId("not an id")));
        assertTrue(failure.getMessage().contains("not shaped like"), failure.getMessage());
    }

    @Test
    void manifestProblemsReachTheCaller() {
        VersionLookupService broken = new VersionLookupService(
                () -> "{}", new ManifestReader(new JsonTextReader()), new VersionScope(Set.of()));
        assertThrows(ManifestFormatException.class, () -> broken.find(new VersionId("99.1")));
    }
}
