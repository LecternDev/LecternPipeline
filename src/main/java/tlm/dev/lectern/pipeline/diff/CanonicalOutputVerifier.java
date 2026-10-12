package tlm.dev.lectern.pipeline.diff;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import tlm.dev.lectern.pipeline.json.CanonicalJsonWriter;
import tlm.dev.lectern.pipeline.json.JsonFormatException;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.json.JsonValue;

public final class CanonicalOutputVerifier {

    private final JsonTextReader reader;
    private final CanonicalJsonWriter writer;

    public CanonicalOutputVerifier(JsonTextReader reader, CanonicalJsonWriter writer) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    public byte[] verified(DiffSide from, DiffSide to, String stdout) {
        byte[] written = stdout.getBytes(StandardCharsets.UTF_8);
        JsonValue value = parse(from, to, stdout);
        byte[] canonical = writer.toBytes(value);
        int difference = Arrays.mismatch(written, canonical);
        if (difference >= 0) {
            throw new DiffException("Version " + to.id().value() + ": the diff against " + from.id().value()
                    + " is not in canonical JSON form, the diff command and the pipeline disagree at byte " + difference
                    + " of " + written.length + ". Nothing was written");
        }
        requireEnvelope(from, to, value);
        return written;
    }

    private JsonValue parse(DiffSide from, DiffSide to, String stdout) {
        try {
            return reader.read(stdout);
        } catch (JsonFormatException exception) {
            throw new DiffException("Version " + to.id().value() + ": the diff against " + from.id().value()
                    + " is not valid JSON: " + exception.getMessage() + ". Nothing was written", exception);
        }
    }

    private static void requireEnvelope(DiffSide from, DiffSide to, JsonValue value) {
        requireSide("from", from, to, value);
        requireSide("to", to, to, value);
    }

    private static void requireSide(String key, DiffSide expected, DiffSide to, JsonValue value) {
        String found = describe(key, value);
        String wanted = expected.id().value() + " " + expected.kind().label();
        if (!wanted.equals(found)) {
            throw new DiffException("Version " + to.id().value() + ": the diff names '" + found + "' as its '" + key + "' version, expected '"
                    + wanted + "'. Nothing was written");
        }
    }

    private static String describe(String key, JsonValue value) {
        return objectOf(value)
                .flatMap(root -> root.get(key))
                .flatMap(CanonicalOutputVerifier::objectOf)
                .map(side -> textOf(side, "id") + " " + textOf(side, "kind"))
                .orElse("none");
    }

    private static Optional<JsonValue.ObjectValue> objectOf(JsonValue value) {
        return value instanceof JsonValue.ObjectValue object ? Optional.of(object) : Optional.empty();
    }

    private static String textOf(JsonValue.ObjectValue object, String key) {
        return object.get(key)
                .filter(JsonValue.StringValue.class::isInstance)
                .map(JsonValue.StringValue.class::cast)
                .map(JsonValue.StringValue::value)
                .orElse("?");
    }
}
