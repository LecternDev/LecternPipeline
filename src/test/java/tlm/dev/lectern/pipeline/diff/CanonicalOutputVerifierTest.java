package tlm.dev.lectern.pipeline.diff;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.fixtures.DiffFixtures;
import tlm.dev.lectern.pipeline.json.CanonicalJsonWriter;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.model.VersionKind;

class CanonicalOutputVerifierTest {

    private static final DiffSide FROM = new DiffSide(new VersionId("99.1-rc-1"), new VersionKind.ReleaseCandidate(), Path.of("from"));
    private static final DiffSide TO = new DiffSide(new VersionId("99.1"), new VersionKind.Release(), Path.of("to"));

    private final CanonicalOutputVerifier verifier = new CanonicalOutputVerifier(new JsonTextReader(), new CanonicalJsonWriter());

    private static String canonical(int changes) {
        return DiffFixtures.canonicalDiff("99.1-rc-1", "release-candidate", "99.1", "release", changes);
    }

    private DiffException refusal(String stdout) {
        return assertThrows(DiffException.class, () -> verifier.verified(FROM, TO, stdout));
    }

    @Test
    void canonicalOutputIsAcceptedAndReturnedAsTheSameBytes() {
        String text = canonical(3);
        assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), verifier.verified(FROM, TO, text));
    }

    @Test
    void emptyChangeListIsAccepted() {
        String text = canonical(0);
        assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), verifier.verified(FROM, TO, text));
    }

    @Test
    void keysInAnotherOrderAreNotCanonical() {
        DiffException failure = refusal("{\"to\": {\"id\": \"99.1\", \"kind\": \"release\"}, \"from\": {\"id\": \"99.1-rc-1\", \"kind\": \"release-candidate\"}, \"changes\": [], \"schema_version\": 1}\n");
        assertTrue(failure.getMessage().contains("not in canonical JSON form"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Version 99.1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("99.1-rc-1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Nothing was written"), failure.getMessage());
    }

    @Test
    void missingFinalNewlineIsNotCanonical() {
        String text = canonical(1);
        DiffException failure = refusal(text.substring(0, text.length() - 1));
        assertTrue(failure.getMessage().contains("not in canonical JSON form"), failure.getMessage());
    }

    @Test
    void carriageReturnsAreNotCanonical() {
        DiffException failure = refusal(canonical(1).replace("\n", "\r\n"));
        assertTrue(failure.getMessage().contains("not in canonical JSON form"), failure.getMessage());
    }

    @Test
    void firstDifferingByteIsNamed() {
        String text = canonical(1).replace("  \"changes\"", "    \"changes\"");
        DiffException failure = refusal(text);
        int expected = text.indexOf("    \"changes\"") + 2;
        assertTrue(failure.getMessage().contains("at byte " + expected + " of " + text.length()), failure.getMessage());
    }

    @Test
    void textThatIsNotJsonIsRefused() {
        DiffException failure = refusal("error: something else\n");
        assertTrue(failure.getMessage().contains("not valid JSON"), failure.getMessage());
    }

    @Test
    void emptyOutputIsRefused() {
        DiffException failure = refusal("");
        assertTrue(failure.getMessage().contains("not valid JSON"), failure.getMessage());
    }

    @Test
    void diffThatNamesAnotherFromVersionIsRefused() {
        String other = DiffFixtures.canonicalDiff("99.0", "release", "99.1", "release", 1);
        DiffException failure = refusal(other);
        assertTrue(failure.getMessage().contains("'99.0 release' as its 'from' version, expected '99.1-rc-1 release-candidate'"), failure.getMessage());
    }

    @Test
    void diffThatNamesAnotherKindIsRefused() {
        String other = DiffFixtures.canonicalDiff("99.1-rc-1", "snapshot", "99.1", "release", 1);
        DiffException failure = refusal(other);
        assertTrue(failure.getMessage().contains("'99.1-rc-1 snapshot'"), failure.getMessage());
    }

    @Test
    void diffThatNamesAnotherToVersionIsRefused() {
        String other = DiffFixtures.canonicalDiff("99.1-rc-1", "release-candidate", "99.2", "release", 1);
        DiffException failure = refusal(other);
        assertTrue(failure.getMessage().contains("'99.2 release' as its 'to' version"), failure.getMessage());
    }

    @Test
    void diffWithoutTheVersionObjectsIsRefused() {
        DiffException failure = refusal("[]\n");
        assertTrue(failure.getMessage().contains("'none'"), failure.getMessage());
    }
}
