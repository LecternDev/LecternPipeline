package tlm.dev.lectern.pipeline.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonTextReaderTest {

    private final JsonTextReader reader = new JsonTextReader();

    @Test
    void everyKindOfValueIsRead() {
        JsonValue value = reader.read("{\"text\": \"a\", \"count\": 3, \"ratio\": 0.5, \"on\": true, \"off\": false, \"none\": null, \"list\": [1, \"b\"]}");
        JsonValue.ObjectValue object = (JsonValue.ObjectValue) value;
        assertEquals(new JsonValue.StringValue("a"), object.members().get("text"));
        assertEquals(new JsonValue.NumberValue("3"), object.members().get("count"));
        assertEquals(new JsonValue.NumberValue("0.5"), object.members().get("ratio"));
        assertEquals(new JsonValue.BooleanValue(true), object.members().get("on"));
        assertEquals(new JsonValue.BooleanValue(false), object.members().get("off"));
        assertEquals(new JsonValue.NullValue(), object.members().get("none"));
        assertEquals(
                new JsonValue.ArrayValue(List.of(new JsonValue.NumberValue("1"), new JsonValue.StringValue("b"))),
                object.members().get("list"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0", "-0.5", "-0", "1E5", "1.50", "12345678901234567890", "0.1000000000000000055511151231257827"})
    void numbersKeepTheirWrittenText(String text) {
        assertEquals(new JsonValue.NumberValue(text), reader.read(text));
    }

    @Test
    void memberOrderIsTheOrderOfTheText() {
        JsonValue.ObjectValue object = (JsonValue.ObjectValue) reader.read("{\"b\": 1, \"a\": 2, \"c\": 3}");
        assertEquals(List.of("b", "a", "c"), List.copyOf(object.members().keySet()));
    }

    @Test
    void nestedStructuresAreRead() {
        JsonValue.ObjectValue root = (JsonValue.ObjectValue) reader.read("{\"outer\": {\"inner\": [[], {}]}}");
        JsonValue.ObjectValue outer = (JsonValue.ObjectValue) root.members().get("outer");
        JsonValue.ArrayValue inner = (JsonValue.ArrayValue) outer.members().get("inner");
        assertEquals(new JsonValue.ArrayValue(List.of()), inner.elements().get(0));
        assertEquals(new JsonValue.ObjectValue(Map.of()), inner.elements().get(1));
    }

    @Test
    void stringEscapesAreDecoded() {
        assertEquals(new JsonValue.StringValue("caf\u00e9 \"q\"\n"), reader.read("\"caf\\u00e9 \\\"q\\\"\\n\""));
    }

    @Test
    void scalarAtTheRootIsAccepted() {
        assertEquals(new JsonValue.StringValue("x"), reader.read("  \"x\"  "));
    }

    @Test
    void getReturnsEmptyForAnAbsentMember() {
        JsonValue.ObjectValue object = (JsonValue.ObjectValue) reader.read("{\"a\": 1}");
        assertEquals(new JsonValue.NumberValue("1"), object.get("a").orElseThrow());
        assertTrue(object.get("b").isEmpty());
    }

    @Test
    void duplicateMemberNameIsRejected() {
        JsonFormatException failure = assertThrows(JsonFormatException.class, () -> reader.read("{\"a\": 1, \"a\": 2}"));
        assertTrue(failure.getMessage().contains("a"), failure.getMessage());
    }

    @Test
    void contentAfterTheValueIsRejected() {
        assertThrows(JsonFormatException.class, () -> reader.read("{} {}"));
        assertThrows(JsonFormatException.class, () -> reader.read("[] x"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "{", "[1, 2", "{\"a\": }", "{\"a\" 1}", "{'a': 1}", "[1,]", "NaN", "{\"a\": 01}", "tru"})
    void malformedTextIsRejected(String text) {
        assertThrows(JsonFormatException.class, () -> reader.read(text));
    }

    @Test
    void syntaxErrorMessageNamesTheLocation() {
        JsonFormatException failure = assertThrows(JsonFormatException.class, () -> reader.read("{\n  \"a\": ,\n}"));
        assertTrue(failure.getMessage().contains("line 2"), failure.getMessage());
    }
}
