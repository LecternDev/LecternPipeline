package tlm.dev.lectern.pipeline.json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CanonicalJsonWriterTest {

    private final JsonTextReader reader = new JsonTextReader();
    private final CanonicalJsonWriter writer = new CanonicalJsonWriter();

    private String canonical(String json) {
        return writer.write(reader.read(json));
    }

    @Test
    void membersAreSortedWhateverTheirOrderInTheTree() {
        Map<String, JsonValue> members = new LinkedHashMap<>();
        members.put("b", new JsonValue.NumberValue("1"));
        members.put("a", new JsonValue.NumberValue("2"));
        assertEquals("{\n  \"a\": 2,\n  \"b\": 1\n}\n", writer.write(new JsonValue.ObjectValue(members)));
    }

    @Test
    void keysAreSortedByUtf16UnitsAndNotByCodePoint() {
        String supplementary = new String(Character.toChars(0x1F600));
        String fullWidth = String.valueOf((char) 0xFF21);
        String json = "{\"" + fullWidth + "\": 1, \"" + supplementary + "\": 2}";
        List<String> lines = canonical(json).lines().toList();
        assertEquals("  \"" + supplementary + "\": 2,", lines.get(1));
        assertEquals("  \"" + fullWidth + "\": 1", lines.get(2));
    }

    @Test
    void numbersKeepTheirWrittenText() {
        assertEquals("[\n  0.0,\n  -0,\n  1E3,\n  2.50,\n  12345678901234567890123\n]\n", canonical("[0.0, -0, 1E3, 2.50, 12345678901234567890123]"));
    }

    @Test
    void deleteAndLineSeparatorAreWrittenAsTheyAre() {
        String text = "a" + (char) 0x7F + "b" + (char) 0x2028 + "c";
        String written = canonical("\"" + text + "\"");
        assertEquals("\"" + text + "\"\n", written);
    }

    @Test
    void quoteBackslashAndControlCharactersAreEscaped() {
        assertEquals("\"q\\\"b\\\\n\\nt\\t\\u0001\\u001F\"\n", canonical("\"q\\\"b\\\\n\\nt\\t\\u0001\\u001f\""));
    }

    @Test
    void emptyContainersAreWrittenOnOneLine() {
        assertEquals("{\n  \"a\": [],\n  \"b\": {}\n}\n", canonical("{\"b\": { }, \"a\": [ ]}"));
    }

    @Test
    void outputUsesTwoSpaceIndentLineFeedsAndOneFinalLineFeed() {
        String written = canonical("{\"x\": [1, {\"y\": null}]}");
        assertEquals("{\n  \"x\": [\n    1,\n    {\n      \"y\": null\n    }\n  ]\n}\n", written);
        assertTrue(!written.contains("\r"));
    }

    @Test
    void toBytesIsTheUtf8EncodingOfWrite() {
        JsonValue value = reader.read("{\"caf" + (char) 233 + "\": \"" + (char) 233 + "\"}");
        assertArrayEquals(writer.write(value).getBytes(StandardCharsets.UTF_8), writer.toBytes(value));
        assertEquals(2, writer.toBytes(reader.read("\"" + (char) 233 + "\"")).length - 3);
    }

    @Test
    void writingWhatWasWrittenGivesTheSameBytes() {
        String once = canonical("{\"z\": [3, 2, {\"b\": 0.50, \"a\": \"x\\ny\"}], \"a\": {}, \"m\": false}");
        assertEquals(once, canonical(once));
    }
}
