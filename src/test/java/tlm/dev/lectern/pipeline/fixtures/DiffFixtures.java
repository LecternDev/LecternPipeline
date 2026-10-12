package tlm.dev.lectern.pipeline.fixtures;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tlm.dev.lectern.pipeline.json.CanonicalJsonWriter;
import tlm.dev.lectern.pipeline.json.JsonValue;

public final class DiffFixtures {

    private DiffFixtures() {
    }

    public static String canonicalDiff(String fromId, String fromKind, String toId, String toKind, int changes) {
        return new String(new CanonicalJsonWriter().toBytes(envelope(fromId, fromKind, toId, toKind, changes)), StandardCharsets.UTF_8);
    }

    public static JsonValue.ObjectValue envelope(String fromId, String fromKind, String toId, String toKind, int changes) {
        List<JsonValue> list = new ArrayList<>();
        for (int index = 0; index < changes; index++) {
            Map<String, JsonValue> change = new LinkedHashMap<>();
            change.put("type", new JsonValue.StringValue("registry.entry_added"));
            change.put("registry", new JsonValue.StringValue("demo:gadget"));
            change.put("entry", new JsonValue.StringValue("demo:gadget_" + index));
            change.put("protocol_id", new JsonValue.NumberValue(Integer.toString(index)));
            list.add(new JsonValue.ObjectValue(change));
        }
        Map<String, JsonValue> members = new LinkedHashMap<>();
        members.put("schema_version", new JsonValue.NumberValue("1"));
        members.put("from", side(fromId, fromKind));
        members.put("to", side(toId, toKind));
        members.put("changes", new JsonValue.ArrayValue(list));
        return new JsonValue.ObjectValue(members);
    }

    private static JsonValue side(String id, String kind) {
        Map<String, JsonValue> members = new LinkedHashMap<>();
        members.put("id", new JsonValue.StringValue(id));
        members.put("kind", new JsonValue.StringValue(kind));
        return new JsonValue.ObjectValue(members);
    }
}
