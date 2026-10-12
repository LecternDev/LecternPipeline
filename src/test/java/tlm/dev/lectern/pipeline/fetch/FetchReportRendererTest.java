package tlm.dev.lectern.pipeline.fetch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;

class FetchReportRendererTest {

    private static final Sha1 SHA1 = new Sha1("0123456789abcdef0123456789abcdef01234567");

    private static FetchedJar fetched(boolean downloaded) {
        return new FetchedJar(new VersionId("99.1"), Path.of("jars", "99.1", "server.jar"),
                new ServerDownload(SHA1, 4242, URI.create("https://downloads.example.invalid/s.jar")), downloaded);
    }

    @Test
    void downloadedJarIsReportedWithItsPathSizeAndHash() {
        assertEquals("""
                fetched 99.1 (downloaded)
                  jar    %s
                  bytes  4242
                  sha1   0123456789abcdef0123456789abcdef01234567
                """.formatted(Path.of("jars", "99.1", "server.jar")), new FetchReportRenderer().render(fetched(true)));
    }

    @Test
    void jarAlreadyThereIsReportedAsSuch() {
        assertEquals(true, new FetchReportRenderer().render(fetched(false)).startsWith("fetched 99.1 (already present)\n"));
    }
}
