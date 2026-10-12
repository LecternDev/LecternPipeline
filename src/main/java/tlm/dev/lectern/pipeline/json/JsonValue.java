package tlm.dev.lectern.pipeline.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public sealed interface JsonValue {

    record ObjectValue(Map<String, JsonValue> members) implements JsonValue {

        public ObjectValue {
            members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
        }

        public Optional<JsonValue> get(String name) {
            return Optional.ofNullable(members.get(name));
        }
    }

    record ArrayValue(List<JsonValue> elements) implements JsonValue {

        public ArrayValue {
            elements = List.copyOf(elements);
        }
    }

    record StringValue(String value) implements JsonValue {

        public StringValue {
            Objects.requireNonNull(value, "value");
        }
    }

    record NumberValue(String text) implements JsonValue {

        public NumberValue {
            Objects.requireNonNull(text, "text");
        }
    }

    record BooleanValue(boolean value) implements JsonValue {
    }

    record NullValue() implements JsonValue {
    }
}
