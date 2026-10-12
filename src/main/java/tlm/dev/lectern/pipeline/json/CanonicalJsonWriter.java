package tlm.dev.lectern.pipeline.json;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.core.io.JsonStringEncoder;

public final class CanonicalJsonWriter {

    private static final String INDENT = "  ";

    private final JsonStringEncoder encoder = JsonStringEncoder.getInstance();

    public String write(JsonValue value) {
        StringBuilder out = new StringBuilder();
        append(out, value, 0);
        out.append('\n');
        return out.toString();
    }

    public byte[] toBytes(JsonValue value) {
        return write(value).getBytes(StandardCharsets.UTF_8);
    }

    private void append(StringBuilder out, JsonValue value, int depth) {
        switch (value) {
            case JsonValue.ObjectValue object -> appendObject(out, object, depth);
            case JsonValue.ArrayValue array -> appendArray(out, array, depth);
            case JsonValue.StringValue string -> appendQuoted(out, string.value());
            case JsonValue.NumberValue number -> out.append(number.text());
            case JsonValue.BooleanValue bool -> out.append(bool.value());
            case JsonValue.NullValue _ -> out.append("null");
        }
    }

    private void appendObject(StringBuilder out, JsonValue.ObjectValue object, int depth) {
        if (object.members().isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        boolean first = true;
        for (Map.Entry<String, JsonValue> member : new TreeMap<>(object.members()).entrySet()) {
            if (!first) {
                out.append(",\n");
            }
            first = false;
            indent(out, depth + 1);
            appendQuoted(out, member.getKey());
            out.append(": ");
            append(out, member.getValue(), depth + 1);
        }
        out.append('\n');
        indent(out, depth);
        out.append('}');
    }

    private void appendArray(StringBuilder out, JsonValue.ArrayValue array, int depth) {
        List<JsonValue> elements = array.elements();
        if (elements.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append("[\n");
        for (int index = 0; index < elements.size(); index++) {
            if (index > 0) {
                out.append(",\n");
            }
            indent(out, depth + 1);
            append(out, elements.get(index), depth + 1);
        }
        out.append('\n');
        indent(out, depth);
        out.append(']');
    }

    private void appendQuoted(StringBuilder out, String text) {
        out.append('"');
        encoder.quoteAsString(text, out);
        out.append('"');
    }

    private static void indent(StringBuilder out, int depth) {
        for (int level = 0; level < depth; level++) {
            out.append(INDENT);
        }
    }
}
