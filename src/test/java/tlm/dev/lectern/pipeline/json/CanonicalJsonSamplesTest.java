package tlm.dev.lectern.pipeline.json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CanonicalJsonSamplesTest {

    private static final Path SAMPLES = Path.of("src/test/resources/canonical-json");
    private static final String INPUT_SUFFIX = ".input.json";
    private static final String EXPECTED_SUFFIX = ".expected.json";
    private static final int PAIRS_COPIED_FROM_LECTERNDIFF = 11;

    private final JsonTextReader reader = new JsonTextReader();
    private final CanonicalJsonWriter writer = new CanonicalJsonWriter();

    private List<String> caseNames() throws IOException {
        try (Stream<Path> files = Files.list(SAMPLES)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(INPUT_SUFFIX))
                    .map(name -> name.substring(0, name.length() - INPUT_SUFFIX.length()))
                    .sorted()
                    .toList();
        }
    }

    private static byte[] bytes(String caseName, String suffix) throws IOException {
        return Files.readAllBytes(SAMPLES.resolve(caseName + suffix));
    }

    private byte[] canonical(byte[] json) {
        return writer.toBytes(reader.read(new String(json, StandardCharsets.UTF_8)));
    }

    @Test
    void everyPairCopiedFromLecternDiffIsThere() throws IOException {
        assertEquals(PAIRS_COPIED_FROM_LECTERNDIFF, caseNames().size(), caseNames().toString());
        for (String name : caseNames()) {
            assertTrue(Files.isRegularFile(SAMPLES.resolve(name + EXPECTED_SUFFIX)), name + " has no expected file");
        }
    }

    @Test
    void writingTheInputGivesTheExpectedBytesOfEveryPair() throws IOException {
        for (String name : caseNames()) {
            assertArrayEquals(bytes(name, EXPECTED_SUFFIX), canonical(bytes(name, INPUT_SUFFIX)), name);
        }
    }

    @Test
    void rewritingAnExpectedFileGivesTheSameBytes() throws IOException {
        for (String name : caseNames()) {
            assertArrayEquals(bytes(name, EXPECTED_SUFFIX), canonical(bytes(name, EXPECTED_SUFFIX)), name);
        }
    }

    @Test
    void expectedFilesAreUtf8WithoutByteOrderMarkAndWithOneFinalLineFeed() throws IOException {
        for (String name : caseNames()) {
            byte[] expected = bytes(name, EXPECTED_SUFFIX);
            assertTrue(expected.length > 1 && expected[expected.length - 1] == '\n', name);
            assertTrue(expected[expected.length - 2] != '\n', name + " ends with a blank line");
            boolean bom = expected.length >= 3 && (expected[0] & 0xFF) == 0xEF && (expected[1] & 0xFF) == 0xBB && (expected[2] & 0xFF) == 0xBF;
            assertTrue(!bom, name + " starts with a byte order mark");
            for (byte value : expected) {
                assertTrue(value != '\r', name + " contains a carriage return");
            }
        }
    }
}
