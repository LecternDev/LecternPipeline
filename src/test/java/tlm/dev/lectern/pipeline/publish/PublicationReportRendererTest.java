package tlm.dev.lectern.pipeline.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.model.VersionId;

class PublicationReportRendererTest {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void reportNamesTheCommitTheTagTheRawChangesTheSheetAndTheDiffs() {
        Path repository = Path.of("data-repository");
        PublicationResult result = new PublicationResult(new VersionId("99.1"), repository, COMMIT, 10038, new TreeChanges(3, 2, 1),
                List.of("step.json", "release.json"));
        assertEquals("""
                published 99.1
                  repository  %s
                  commit      %s data: 99.1
                  tag         99.1
                  raw files   10038 (3 added, 2 changed, 1 removed)
                  version     versions/99.1/meta.json
                  diffs       diffs/99.1/step.json, diffs/99.1/release.json
                  pushed      no, nothing is ever pushed by this command
                """.formatted(repository, COMMIT), new PublicationReportRenderer().render(result));
    }

    @Test
    void reportSaysNoneWhenThereIsNoDiff() {
        Path repository = Path.of("data-repository");
        PublicationResult result = new PublicationResult(new VersionId("1.14.4"), repository, COMMIT, 2415, new TreeChanges(2415, 0, 0), List.of());
        assertEquals("""
                published 1.14.4
                  repository  %s
                  commit      %s data: 1.14.4
                  tag         1.14.4
                  raw files   2415 (2415 added, 0 changed, 0 removed)
                  version     versions/1.14.4/meta.json
                  diffs       none
                  pushed      no, nothing is ever pushed by this command
                """.formatted(repository, COMMIT), new PublicationReportRenderer().render(result));
    }

    @Test
    void gitAttributesForTheDataRepositoryAreExactlyTheFourLines() {
        assertEquals("reports/** -text\ndata/** -text\nversions/** -text\ndiffs/** -text\n", DataRepositoryRules.GITATTRIBUTES);
    }

    @Test
    void pathsOfTheDataRepositoryFollowTheLayout() {
        assertEquals("versions/99.1", DataRepositoryRules.versionFolder("99.1"));
        assertEquals("versions/99.1/meta.json", DataRepositoryRules.metaPath("99.1"));
        assertEquals("diffs/99.1", DataRepositoryRules.diffsFolder("99.1"));
        assertEquals("diffs/99.1/step.json", DataRepositoryRules.diffPath("99.1", DataRepositoryRules.STEP_DIFF_FILE));
        assertEquals("diffs/99.1/release.json", DataRepositoryRules.diffPath("99.1", DataRepositoryRules.RELEASE_DIFF_FILE));
        assertEquals("data: 99.1", DataRepositoryRules.commitTitle("99.1"));
        assertEquals(List.of("reports", "data"), DataRepositoryRules.RAW_FOLDERS);
    }

    @Test
    void everyFolderOfTheLayoutIsCoveredByAnAttributeLine() {
        for (String folder : List.of("reports", "data", "versions", "diffs")) {
            assertEquals(1, DataRepositoryRules.GITATTRIBUTES.lines().filter(line -> line.equals(folder + "/** -text")).count(), folder);
        }
        assertEquals(4, DataRepositoryRules.attributeProbePaths("99.1").size());
    }
}
