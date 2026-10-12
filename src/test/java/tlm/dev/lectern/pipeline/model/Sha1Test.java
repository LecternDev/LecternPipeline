package tlm.dev.lectern.pipeline.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Sha1Test {

    @Test
    void sha1KeepsFortyLowercaseHexCharacters() {
        String hex = "0123456789abcdef0123456789abcdef01234567";
        assertEquals(hex, new Sha1(hex).hex());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "0123456789abcdef0123456789abcdef0123456",
            "0123456789abcdef0123456789abcdef012345678",
            "0123456789ABCDEF0123456789ABCDEF01234567",
            "0123456789abcdef0123456789abcdef0123456g",
            " 123456789abcdef0123456789abcdef01234567"})
    void sha1RejectsAnythingElse(String value) {
        assertThrows(IllegalArgumentException.class, () -> new Sha1(value));
    }

    @Test
    void sha1RejectsNull() {
        assertThrows(NullPointerException.class, () -> new Sha1(null));
    }
}
