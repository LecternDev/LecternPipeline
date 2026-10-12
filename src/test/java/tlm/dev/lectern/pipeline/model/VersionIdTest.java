package tlm.dev.lectern.pipeline.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VersionIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"26.3", "26.3-rc-3", "25w46a", "1.14 Pre-Release 1", "3D Shareware v1.34"})
    void idKeepsTheManifestValueVerbatim(String value) {
        assertEquals(value, new VersionId(value).value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", " 26.3", "26.3 ", "26/3", "26\\3", "26.3\n", "26\u00003"})
    void idRejectsBlankSurroundingWhitespaceSeparatorsAndControlCharacters(String value) {
        assertThrows(IllegalArgumentException.class, () -> new VersionId(value));
    }

    @Test
    void idRejectsNull() {
        assertThrows(NullPointerException.class, () -> new VersionId(null));
    }
}
