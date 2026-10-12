package tlm.dev.lectern.pipeline.publish;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.model.VersionId;

class StagedSetVerifierTest {

    private static final VersionId ID = new VersionId("99.2");
    private static final TreePlan PLAN = new TreePlan(Set.of("reports/a.json", "reports/b.json", "data/c.json"), Set.of("reports/b.json", "data/c.json", "data/old.json"));
    private static final Set<String> EXTRA = Set.of("versions/99.2/meta.json", "diffs/99.2/step.json");

    private static String exactlyExpected() {
        return "A\0reports/a.json\0M\0reports/b.json\0D\0data/old.json\0A\0versions/99.2/meta.json\0A\0diffs/99.2/step.json\0";
    }

    private PublicationException refusal(String output) {
        return assertThrows(PublicationException.class, () -> StagedSetVerifier.verify(ID, "staged", PLAN, EXTRA, StagedSet.parse(output)));
    }

    @Test
    void exactlyTheExpectedChangesAreAccepted() {
        assertDoesNotThrow(() -> StagedSetVerifier.verify(ID, "staged", PLAN, EXTRA, StagedSet.parse(exactlyExpected())));
    }

    @Test
    void keptFileThatDidNotChangeIsNotRequiredToBeStaged() {
        assertDoesNotThrow(() -> StagedSetVerifier.verify(ID, "staged",
                PLAN, EXTRA, StagedSet.parse("A\0reports/a.json\0D\0data/old.json\0A\0versions/99.2/meta.json\0A\0diffs/99.2/step.json\0")));
    }

    @Test
    void stagedFileOutsideThePublishedSetIsUnexpected() {
        PublicationException failure = refusal(exactlyExpected() + "A\0notes.txt\0");
        assertTrue(failure.getMessage().contains("Unexpected 1 [A notes.txt]"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Version 99.2"), failure.getMessage());
    }

    @Test
    void newFileThatWasNotStagedIsMissing() {
        PublicationException failure = refusal("M\0reports/b.json\0D\0data/old.json\0A\0versions/99.2/meta.json\0A\0diffs/99.2/step.json\0");
        assertTrue(failure.getMessage().contains("missing 1 [A reports/a.json]"), failure.getMessage());
    }

    @Test
    void staleFileThatWasNotDeletedIsMissing() {
        PublicationException failure = refusal("A\0reports/a.json\0M\0reports/b.json\0A\0versions/99.2/meta.json\0A\0diffs/99.2/step.json\0");
        assertTrue(failure.getMessage().contains("missing 1 [D data/old.json]"), failure.getMessage());
    }

    @Test
    void sheetOrDiffThatWasNotStagedIsMissing() {
        PublicationException failure = refusal("A\0reports/a.json\0D\0data/old.json\0A\0versions/99.2/meta.json\0");
        assertTrue(failure.getMessage().contains("A diffs/99.2/step.json"), failure.getMessage());
    }

    @Test
    void modificationOfAFileThatIsNotKeptIsUnexpected() {
        PublicationException failure = refusal(exactlyExpected() + "M\0reports/other.json\0");
        assertTrue(failure.getMessage().contains("M reports/other.json"), failure.getMessage());
    }

    @Test
    void deletionOfAFileThatIsStillPublishedIsUnexpected() {
        PublicationException failure = refusal(exactlyExpected() + "D\0data/c.json\0");
        assertTrue(failure.getMessage().contains("D data/c.json"), failure.getMessage());
    }

    @Test
    void typeChangeAndOtherStatusesAreUnexpected() {
        PublicationException failure = refusal(exactlyExpected() + "T\0reports/b.json\0");
        assertTrue(failure.getMessage().contains("Unexpected"), failure.getMessage());
    }

    @Test
    void messageNamesAtMostFiveExamplesOfEachKind() {
        String many = "A\0notes/1\0A\0notes/2\0A\0notes/3\0A\0notes/4\0A\0notes/5\0A\0notes/6\0A\0notes/7\0";
        PublicationException failure = refusal(exactlyExpected() + many);
        assertTrue(failure.getMessage().contains("Unexpected 7"), failure.getMessage());
        assertTrue(failure.getMessage().contains("notes/5"), failure.getMessage());
        assertTrue(!failure.getMessage().contains("notes/6"), failure.getMessage());
    }

    @Test
    void committedSetMustEqualTheStagedSet() {
        StagedSet staged = StagedSet.parse(exactlyExpected());
        assertDoesNotThrow(() -> StagedSetVerifier.verifySame(ID, staged, StagedSet.parse(exactlyExpected())));
        PublicationException failure = assertThrows(PublicationException.class,
                () -> StagedSetVerifier.verifySame(ID, staged, StagedSet.parse(exactlyExpected() + "A\0hook.txt\0")));
        assertTrue(failure.getMessage().contains("committed files are not exactly the staged ones"), failure.getMessage());
        assertTrue(failure.getMessage().contains("A hook.txt"), failure.getMessage());
    }

    @Test
    void committedSetThatLostAFileIsReported() {
        StagedSet staged = StagedSet.parse(exactlyExpected());
        PublicationException failure = assertThrows(PublicationException.class,
                () -> StagedSetVerifier.verifySame(ID, staged, StagedSet.parse("A\0reports/a.json\0")));
        assertTrue(failure.getMessage().contains("missing 4"), failure.getMessage());
    }
}
