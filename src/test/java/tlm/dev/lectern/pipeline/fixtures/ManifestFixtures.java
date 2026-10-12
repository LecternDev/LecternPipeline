package tlm.dev.lectern.pipeline.fixtures;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import tlm.dev.lectern.pipeline.manifest.ManifestEntry;
import tlm.dev.lectern.pipeline.manifest.TrackedVersion;
import tlm.dev.lectern.pipeline.manifest.VersionFormParser;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class ManifestFixtures {

    public static final Instant ORIGIN = Instant.parse("2090-01-01T00:00:00Z");

    private ManifestFixtures() {
    }

    public static Instant hoursAfterOrigin(long hours) {
        return ORIGIN.plus(Duration.ofHours(hours));
    }

    public static String sha1Of(String id) {
        return String.format("%040x", id.hashCode() & 0xffffffffL);
    }

    public static String entryJson(String id, String type, Instant releaseTime) {
        return "{\"id\": \"" + id + "\", \"type\": \"" + type + "\", "
                + "\"url\": \"https://manifest.example.invalid/v1/packages/" + sha1Of(id) + "/version.json\", "
                + "\"time\": \"2090-12-31T00:00:00+00:00\", "
                + "\"releaseTime\": \"" + releaseTime + "\", "
                + "\"sha1\": \"" + sha1Of(id) + "\", \"complianceLevel\": 1}";
    }

    public static String manifestJson(String... entries) {
        return "{\"latest\": {\"release\": \"none\", \"snapshot\": \"none\"}, \"versions\": ["
                + String.join(", ", entries) + "]}";
    }

    public static String manifestJson(List<ManifestEntry> entries) {
        return manifestJson(entries.stream()
                .map(entry -> entryJson(entry.id().value(), entry.type(), entry.releaseTime()))
                .collect(Collectors.toList())
                .toArray(new String[0]));
    }

    public static ManifestEntry entry(String id, String type, Instant releaseTime) {
        String sha1 = sha1Of(id);
        return new ManifestEntry(
                new VersionId(id),
                type,
                URI.create("https://manifest.example.invalid/v1/packages/" + sha1 + "/version.json"),
                releaseTime,
                new Sha1(sha1));
    }

    public static TrackedVersion tracked(String id, long hoursAfterOrigin) {
        VersionId versionId = new VersionId(id);
        return new TrackedVersion(
                entry(id, "snapshot", hoursAfterOrigin(hoursAfterOrigin)),
                VersionFormParser.parse(versionId).orElseThrow(() -> new IllegalArgumentException("Not a tracked form: " + id)));
    }
}
