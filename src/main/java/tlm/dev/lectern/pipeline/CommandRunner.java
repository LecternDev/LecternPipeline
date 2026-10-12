package tlm.dev.lectern.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.MissingResourceException;
import java.util.Set;
import tlm.dev.lectern.pipeline.extract.ExtractionReportRenderer;
import tlm.dev.lectern.pipeline.extract.ExtractionResult;
import tlm.dev.lectern.pipeline.extract.ExtractionService;
import tlm.dev.lectern.pipeline.extract.ExtractionSettings;
import tlm.dev.lectern.pipeline.extract.JarEraDetector;
import tlm.dev.lectern.pipeline.fetch.FetchReportRenderer;
import tlm.dev.lectern.pipeline.fetch.FetchedJar;
import tlm.dev.lectern.pipeline.fetch.JarStore;
import tlm.dev.lectern.pipeline.fetch.ServerJarService;
import tlm.dev.lectern.pipeline.fetch.Sha1Hasher;
import tlm.dev.lectern.pipeline.fetch.VersionJsonReader;
import tlm.dev.lectern.pipeline.fetch.VersionJsonRef;
import tlm.dev.lectern.pipeline.http.HttpDownloadClient;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.manifest.ExcludedVersionsReader;
import tlm.dev.lectern.pipeline.manifest.ManifestClient;
import tlm.dev.lectern.pipeline.manifest.ManifestEntry;
import tlm.dev.lectern.pipeline.manifest.ManifestReader;
import tlm.dev.lectern.pipeline.manifest.RecentVersionsService;
import tlm.dev.lectern.pipeline.manifest.TrackedVersion;
import tlm.dev.lectern.pipeline.manifest.VersionListRenderer;
import tlm.dev.lectern.pipeline.manifest.VersionLookupService;
import tlm.dev.lectern.pipeline.manifest.VersionScope;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.process.ProcessLauncher;

final class CommandRunner {

    static final int LIST_SIZE = 15;

    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration TRANSFER_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration MANIFEST_TRANSFER_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration GENERATOR_TIMEOUT = Duration.ofMinutes(10);
    private static final String EXCLUDED_VERSIONS_RESOURCE = "/excluded-versions.txt";

    private final PipelineConfig config;
    private final HttpClient http;
    private final ProcessLauncher launcher;
    private final Clock clock;
    private final PrintStream out;

    CommandRunner(PipelineConfig config, HttpClient http, ProcessLauncher launcher, Clock clock, PrintStream out) {
        this.config = config;
        this.http = http;
        this.launcher = launcher;
        this.clock = clock;
        this.out = out;
    }

    void run() {
        switch (config.command()) {
            case Command.ListVersions _ -> list();
            case Command.Fetch fetch -> fetch(fetch.id());
            case Command.Extract extract -> extract(extract.id());
        }
    }

    private void list() {
        RecentVersionsService service = new RecentVersionsService(manifestClient(), new ManifestReader(new JsonTextReader()), scope());
        out.print(new VersionListRenderer().render(service.recent(LIST_SIZE)));
    }

    private void fetch(VersionId id) {
        requireWorkbench();
        out.print(new FetchReportRenderer().render(fetchJar(lookup(id))));
    }

    private void extract(VersionId id) {
        requireWorkbench();
        ExtractionService extraction = extractionService();
        extraction.requireFreshOutput(id);
        FetchedJar jar = fetchJar(lookup(id));
        out.print(new FetchReportRenderer().render(jar));
        ExtractionResult result = extraction.extract(id, jar.jar());
        out.print(new ExtractionReportRenderer().render(result));
    }

    private TrackedVersion lookup(VersionId id) {
        return new VersionLookupService(manifestClient(), new ManifestReader(new JsonTextReader()), scope()).find(id);
    }

    private FetchedJar fetchJar(TrackedVersion version) {
        ManifestEntry entry = version.entry();
        ServerJarService service = new ServerJarService(
                new HttpDownloadClient(http, RESPONSE_TIMEOUT, TRANSFER_TIMEOUT),
                new JarStore(config.workbench().resolve("jars")),
                new VersionJsonReader(new JsonTextReader()),
                new Sha1Hasher());
        return service.ensure(new VersionJsonRef(entry.id(), entry.url(), entry.sha1()));
    }

    private ExtractionService extractionService() {
        ExtractionSettings settings = new ExtractionSettings(
                config.workbench().resolve("generated"), config.scratchRoot(), config.java(), GENERATOR_TIMEOUT);
        return new ExtractionService(launcher, new JarEraDetector(), settings, clock);
    }

    private ManifestClient manifestClient() {
        return new ManifestClient(new HttpDownloadClient(http, RESPONSE_TIMEOUT, MANIFEST_TRANSFER_TIMEOUT), config.manifestUrl());
    }

    private void requireWorkbench() {
        Path workbench = config.workbench();
        if (!Files.isDirectory(workbench)) {
            throw new WorkbenchMissingException(workbench);
        }
    }

    private static VersionScope scope() {
        return new VersionScope(excludedVersions());
    }

    private static Set<VersionId> excludedVersions() {
        try (InputStream stream = CommandRunner.class.getResourceAsStream(EXCLUDED_VERSIONS_RESOURCE)) {
            if (stream == null) {
                throw new MissingResourceException(
                        "The resource " + EXCLUDED_VERSIONS_RESOURCE + " is not packaged", CommandRunner.class.getName(), EXCLUDED_VERSIONS_RESOURCE);
            }
            return new ExcludedVersionsReader().parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + EXCLUDED_VERSIONS_RESOURCE, exception);
        }
    }
}
