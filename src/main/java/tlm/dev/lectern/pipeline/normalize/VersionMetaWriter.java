package tlm.dev.lectern.pipeline.normalize;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import tlm.dev.lectern.pipeline.json.CanonicalJsonWriter;
import tlm.dev.lectern.pipeline.json.JsonValue;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class VersionMetaWriter {

    public static final int SCHEMA_VERSION = 1;

    private final CanonicalJsonWriter writer;

    public VersionMetaWriter(CanonicalJsonWriter writer) {
        this.writer = writer;
    }

    public JsonValue.ObjectValue toJson(VersionMeta meta) {
        Map<String, JsonValue> members = new LinkedHashMap<>();
        members.put("schema_version", new JsonValue.NumberValue(Integer.toString(SCHEMA_VERSION)));
        members.put("id", new JsonValue.StringValue(meta.id().value()));
        members.put("kind", new JsonValue.StringValue(meta.kind().label()));
        members.put("release_time", new JsonValue.StringValue(DateTimeFormatter.ISO_INSTANT.format(meta.releaseTime())));
        members.put("step_base", idOrNull(meta.stepBase()));
        members.put("release_base", idOrNull(meta.releaseBase()));
        members.put("jar_sha1", new JsonValue.StringValue(meta.jarSha1().hex()));
        meta.game().ifPresent(game -> members.put("game", game));
        return new JsonValue.ObjectValue(members);
    }

    public byte[] toBytes(VersionMeta meta) {
        return writer.toBytes(toJson(meta));
    }

    private static JsonValue idOrNull(Optional<VersionId> id) {
        return id.<JsonValue>map(value -> new JsonValue.StringValue(value.value())).orElseGet(JsonValue.NullValue::new);
    }
}
