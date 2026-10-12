package tlm.dev.lectern.pipeline.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.model.VersionId;

class PublicationReportRendererTest {

    @Test
    void reportNamesTheCommitTheFilesAndSaysNothingWasPushed() {
        Path repository = Path.of("data-repository");
        PublicationResult result = new PublicationResult(new VersionId("99.1"), repository, 10039, "0123456789abcdef0123456789abcdef01234567");
        assertEquals("""
                published 99.1
                  repository  %s
                  commit      0123456789abcdef0123456789abcdef01234567 data: 99.1
                  files       10039 under versions/99.1/
                  pushed      no, nothing is ever pushed by this command
                """.formatted(repository), new PublicationReportRenderer().render(result));
    }

    @Test
    void gitAttributesForTheDataRepositoryAreExactlyThese() {
        assertEquals("versions/** -text\ndiffs/** -text\n", DataRepositoryRules.GITATTRIBUTES);
        assertEquals("versions/99.1", DataRepositoryRules.versionFolder("99.1"));
    }
}
