package tlm.dev.lectern.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.MissingResourceException;
import java.util.Set;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.manifest.ExcludedVersionsReader;
import tlm.dev.lectern.pipeline.manifest.ManifestClient;
import tlm.dev.lectern.pipeline.manifest.ManifestReader;
import tlm.dev.lectern.pipeline.manifest.RecentVersionsService;
import tlm.dev.lectern.pipeline.manifest.VersionListRenderer;
import tlm.dev.lectern.pipeline.manifest.VersionScope;
import tlm.dev.lectern.pipeline.model.PipelineException;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class PipelineMain {

    static final int LIST_SIZE = 15;

    private static final int EXIT_OK = 0;
    private static final int EXIT_FAILURE = 1;
    private static final int EXIT_USAGE = 2;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String EXCLUDED_VERSIONS_RESOURCE = "/excluded-versions.txt";

    private final PrintStream out;
    private final PrintStream err;

    PipelineMain(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        System.exit(new PipelineMain(System.out, System.err).run(args));
    }

    int run(String[] args) {
        try {
            PipelineConfig config = new PipelineConfigReader().read(args);
            out.print(list(config));
            out.flush();
            return EXIT_OK;
        } catch (UsageException exception) {
            err.println(exception.getMessage());
            return EXIT_USAGE;
        } catch (PipelineException exception) {
            err.println("lectern-pipeline: " + exception.getMessage());
            return EXIT_FAILURE;
        }
    }

    private static String list(PipelineConfig config) {
        VersionScope scope = new VersionScope(excludedVersions());
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()) {
            RecentVersionsService service = new RecentVersionsService(
                    new ManifestClient(http, config.manifestUrl(), REQUEST_TIMEOUT),
                    new ManifestReader(new JsonTextReader()),
                    scope);
            return new VersionListRenderer().render(service.recent(LIST_SIZE));
        }
    }

    private static Set<VersionId> excludedVersions() {
        try (InputStream stream = PipelineMain.class.getResourceAsStream(EXCLUDED_VERSIONS_RESOURCE)) {
            if (stream == null) {
                throw new MissingResourceException(
                        "The resource " + EXCLUDED_VERSIONS_RESOURCE + " is not packaged", PipelineMain.class.getName(), EXCLUDED_VERSIONS_RESOURCE);
            }
            return new ExcludedVersionsReader().parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + EXCLUDED_VERSIONS_RESOURCE, exception);
        }
    }
}
