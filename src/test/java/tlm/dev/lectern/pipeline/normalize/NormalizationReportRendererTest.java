package tlm.dev.lectern.pipeline.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.model.VersionId;

class NormalizationReportRendererTest {

    @Test
    void reportNamesFilesRewrittenBytesAndDuration() {
        Path output = Path.of("normalized", "99.1");
        NormalizationResult result = new NormalizationResult(new VersionId("99.1"), output, List.of("a.json", "b.json"), 100, 120, 1, Duration.ofMillis(2250));
        assertEquals("""
                normalized 99.1
                  output    %s
                  files     2 (1 with other bytes than the generator wrote), plus meta.json
                  bytes     100 read, 120 written
                  duration  2.3 s
                """.formatted(output), new NormalizationReportRenderer().render(result));
    }
}
