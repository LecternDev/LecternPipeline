package tlm.dev.lectern.pipeline.normalize;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import tlm.dev.lectern.pipeline.json.JsonValue;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.model.VersionKind;

public record VersionMeta(
        VersionId id,
        VersionKind kind,
        Instant releaseTime,
        Optional<VersionId> stepBase,
        Optional<VersionId> releaseBase,
        Sha1 jarSha1,
        Optional<JsonValue.ObjectValue> game) {

    public VersionMeta {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(releaseTime, "releaseTime");
        Objects.requireNonNull(stepBase, "stepBase");
        Objects.requireNonNull(releaseBase, "releaseBase");
        Objects.requireNonNull(jarSha1, "jarSha1");
        Objects.requireNonNull(game, "game");
    }
}
