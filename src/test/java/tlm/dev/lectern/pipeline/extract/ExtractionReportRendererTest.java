package tlm.dev.lectern.pipeline.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.model.VersionId;

class ExtractionReportRendererTest {

    @Test
    void reportNamesTheIdTheFilesTheBytesAndTheDuration() {
        Path output = Path.of("generated", "99.1");
        ExtractionResult result = new ExtractionResult(new VersionId("99.1"), output, new TreeSummary(10076, 19807842, 10038, 18773223), Duration.ofMillis(6149));
        assertEquals("""
                extracted 99.1
                  output    %s
                  files     10076 (10038 without .cache)
                  bytes     19807842 (18773223 without .cache)
                  duration  6.1 s
                """.formatted(output), new ExtractionReportRenderer().render(result));
    }
}
