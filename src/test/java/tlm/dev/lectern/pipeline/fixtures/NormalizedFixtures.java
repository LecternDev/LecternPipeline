package tlm.dev.lectern.pipeline.fixtures;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import tlm.dev.lectern.pipeline.json.CanonicalJsonWriter;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.model.VersionKind;
import tlm.dev.lectern.pipeline.normalize.VersionMeta;
import tlm.dev.lectern.pipeline.normalize.VersionMetaWriter;

public final class NormalizedFixtures {

    public static final Sha1 JAR_SHA1 = new Sha1("0123456789abcdef0123456789abcdef01234567");

    private NormalizedFixtures() {
    }

    public static byte[] meta(String id, VersionKind kind, Optional<String> stepBase, Optional<String> releaseBase) {
        VersionMeta meta = new VersionMeta(new VersionId(id), kind, Instant.parse("2090-01-02T03:04:05Z"),
                stepBase.map(VersionId::new), releaseBase.map(VersionId::new), JAR_SHA1, Optional.empty());
        return new VersionMetaWriter(new CanonicalJsonWriter()).toBytes(meta);
    }

    public static Path write(Path normalizedRoot, String id, Map<String, String> rawFiles) {
        return write(normalizedRoot, id, new VersionKind.Release(), Optional.empty(), Optional.empty(), rawFiles);
    }

    public static Path write(Path normalizedRoot, String id, VersionKind kind, Optional<String> stepBase, Optional<String> releaseBase,
            Map<String, String> rawFiles) {
        Path folder = normalizedRoot.resolve(id);
        TreeFixtures.write(folder, "meta.json", meta(id, kind, stepBase, releaseBase));
        rawFiles.forEach((relative, text) -> TreeFixtures.write(folder, relative, text.getBytes(StandardCharsets.UTF_8)));
        return folder;
    }
}
