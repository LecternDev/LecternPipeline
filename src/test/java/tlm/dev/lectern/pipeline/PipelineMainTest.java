package tlm.dev.lectern.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.fixtures.LocalHttpServer;
import tlm.dev.lectern.pipeline.fixtures.ManifestFixtures;

class PipelineMainTest {

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PipelineMain main = new PipelineMain(
            new PrintStream(outBytes, true, StandardCharsets.UTF_8),
            new PrintStream(errBytes, true, StandardCharsets.UTF_8));

    private String out() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    private static String manifestWithAnExcludedJokeAndAnUnknownShape() {
        List<String> entries = new ArrayList<>();
        entries.add(ManifestFixtures.entryJson("99.2-snapshot-1", "snapshot", ManifestFixtures.hoursAfterOrigin(300)));
        entries.add(ManifestFixtures.entryJson("26w14a", "snapshot", ManifestFixtures.hoursAfterOrigin(250)));
        entries.add(ManifestFixtures.entryJson("99.1", "release", ManifestFixtures.hoursAfterOrigin(200)));
        entries.add(ManifestFixtures.entryJson("99.1-rc-1", "snapshot", ManifestFixtures.hoursAfterOrigin(100)));
        entries.add(ManifestFixtures.entryJson("1.14 Pre-Release 1", "snapshot", ManifestFixtures.hoursAfterOrigin(50)));
        entries.add(ManifestFixtures.entryJson("98.9", "release", ManifestFixtures.hoursAfterOrigin(10)));
        return ManifestFixtures.manifestJson(entries.toArray(new String[0]));
    }

    @Test
    void listPrintsTheRecentVersionsWithKindReleaseTimeAndDiffBase() {
        try (LocalHttpServer server = new LocalHttpServer(200, manifestWithAnExcludedJokeAndAnUnknownShape())) {
            int exit = main.run(new String[] {"list", "--manifest-url=" + server.manifestUri()});
            assertEquals("", err());
            assertEquals(0, exit);
            assertEquals("""
                    ID               KIND               RELEASED              BASE       RELEASE BASE
                    99.2-snapshot-1  snapshot           2090-01-13T12:00:00Z  99.1       -
                    99.1             release            2090-01-09T08:00:00Z  99.1-rc-1  98.9
                    99.1-rc-1        release-candidate  2090-01-05T04:00:00Z  98.9       -
                    98.9             release            2090-01-01T10:00:00Z  -          -
                    """, out());
        }
    }

    @Test
    void listWithoutArgumentsPrintsTheUsageAndExitsWithTwo() {
        int exit = main.run(new String[] {});
        assertEquals(2, exit);
        assertEquals("", out());
        assertEquals(PipelineConfigReader.USAGE + System.lineSeparator(), err());
    }

    @Test
    void unknownCommandExitsWithTwo() {
        assertEquals(2, main.run(new String[] {"download"}));
        assertTrue(err().contains("Unknown command 'download'"), err());
    }

    @Test
    void fetchFailureExitsWithOneAndNamesTheStatus() {
        try (LocalHttpServer server = new LocalHttpServer(503, "")) {
            int exit = main.run(new String[] {"list", "--manifest-url=" + server.manifestUri()});
            assertEquals(1, exit);
            assertEquals("", out());
            assertTrue(err().startsWith("lectern-pipeline: "), err());
            assertTrue(err().contains("HTTP 503"), err());
        }
    }

    @Test
    void malformedManifestExitsWithOneAndPrintsNothingOnStandardOutput() {
        try (LocalHttpServer server = new LocalHttpServer(200, "{\"versions\": [{\"id\": \"99.1\"}]}")) {
            int exit = main.run(new String[] {"list", "--manifest-url=" + server.manifestUri()});
            assertEquals(1, exit);
            assertEquals("", out());
            assertTrue(err().contains("entry 0"), err());
        }
    }
}
