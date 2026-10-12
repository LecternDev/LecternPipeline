package tlm.dev.lectern.pipeline.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.core.json.JsonFactory;

public final class JsonTextReader {

    private final JsonFactory factory;

    public JsonTextReader() {
        this.factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
    }

    public JsonValue read(String text) {
        Objects.requireNonNull(text, "text");
        try (JsonParser parser = factory.createParser(ObjectReadContext.empty(), text)) {
            JsonToken first = parser.nextToken();
            if (first == null) {
                throw new JsonFormatException("The JSON text is empty");
            }
            JsonValue value = readValue(parser, first);
            if (parser.nextToken() != null) {
                throw new JsonFormatException("Unexpected content after the JSON value" + describe(parser.currentTokenLocation()));
            }
            return value;
        } catch (JacksonException exception) {
            throw new JsonFormatException(exception.getOriginalMessage() + describe(exception.getLocation()), exception);
        }
    }

    private JsonValue readValue(JsonParser parser, JsonToken token) {
        return switch (token) {
            case START_OBJECT -> readObject(parser);
            case START_ARRAY -> readArray(parser);
            case VALUE_STRING -> new JsonValue.StringValue(parser.getString());
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> new JsonValue.NumberValue(parser.getString());
            case VALUE_TRUE -> new JsonValue.BooleanValue(true);
            case VALUE_FALSE -> new JsonValue.BooleanValue(false);
            case VALUE_NULL -> new JsonValue.NullValue();
            default -> throw new JsonFormatException("Unexpected JSON token " + token + describe(parser.currentTokenLocation()));
        };
    }

    private JsonValue readObject(JsonParser parser) {
        Map<String, JsonValue> members = new LinkedHashMap<>();
        for (JsonToken token = parser.nextToken(); token != JsonToken.END_OBJECT; token = parser.nextToken()) {
            if (token != JsonToken.PROPERTY_NAME) {
                throw new JsonFormatException("Expected a property name but found " + token + describe(parser.currentTokenLocation()));
            }
            String name = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            if (valueToken == null) {
                throw new JsonFormatException("The JSON text ends after the property '" + name + "'");
            }
            members.put(name, readValue(parser, valueToken));
        }
        return new JsonValue.ObjectValue(members);
    }

    private JsonValue readArray(JsonParser parser) {
        List<JsonValue> elements = new ArrayList<>();
        for (JsonToken token = parser.nextToken(); token != JsonToken.END_ARRAY; token = parser.nextToken()) {
            if (token == null) {
                throw new JsonFormatException("The JSON text ends inside an array");
            }
            elements.add(readValue(parser, token));
        }
        return new JsonValue.ArrayValue(elements);
    }

    private static String describe(TokenStreamLocation location) {
        if (location == null || location == TokenStreamLocation.NA) {
            return "";
        }
        return " (line " + location.getLineNr() + ", column " + location.getColumnNr() + ")";
    }
}
