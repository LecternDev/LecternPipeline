package tlm.dev.lectern.pipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReleaseLineTest {

    @Test
    void lineWithAPatchNumberIsAPatchLine() {
        assertTrue(new ReleaseLine(26, 1, 2).isPatch());
        assertFalse(new ReleaseLine(26, 1, 0).isPatch());
    }

    @Test
    void sameDropIgnoresThePatchNumber() {
        assertTrue(new ReleaseLine(26, 1, 0).sameDrop(new ReleaseLine(26, 1, 2)));
        assertFalse(new ReleaseLine(26, 1, 0).sameDrop(new ReleaseLine(26, 2, 0)));
        assertFalse(new ReleaseLine(26, 1, 0).sameDrop(new ReleaseLine(27, 1, 0)));
    }

    @Test
    void negativePartIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ReleaseLine(-1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReleaseLine(26, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReleaseLine(26, 1, -1));
    }
}
