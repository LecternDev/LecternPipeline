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
import java.util.Optional;
import java.util.Set;
import tlm.dev.lectern.pipeline.diff.CanonicalOutputVerifier;
import tlm.dev.lectern.pipeline.diff.DiffCommand;
import tlm.dev.lectern.pipeline.diff.DiffCommandClient;
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
import tlm.dev.lectern.pipeline.http.HttpDownloadClient;
import tlm.dev.lectern.pipeline.json.CanonicalJsonWriter;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.manifest.ExcludedVersionsReader;
import tlm.dev.lectern.pipeline.manifest.HistoryRenderer;
import tlm.dev.lectern.pipeline.manifest.HistoryService;
import tlm.dev.lectern.pipeline.manifest.ManifestClient;
import tlm.dev.lectern.pipeline.manifest.ManifestReader;
import tlm.dev.lectern.pipeline.manifest.RecentVersionsService;
import tlm.dev.lectern.pipeline.manifest.VersionListRenderer;
import tlm.dev.lectern.pipeline.manifest.VersionLookupService;
import tlm.dev.lectern.pipeline.manifest.VersionScope;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.normalize.GameInfoReader;
import tlm.dev.lectern.pipeline.normalize.NormalizationService;
import tlm.dev.lectern.pipeline.normalize.NormalizationSettings;
import tlm.dev.lectern.pipeline.normalize.NormalizationTools;
import tlm.dev.lectern.pipeline.normalize.TreeScanner;
import tlm.dev.lectern.pipeline.normalize.VersionMetaReader;
import tlm.dev.lectern.pipeline.normalize.VersionMetaWriter;
import tlm.dev.lectern.pipeline.process.ProcessLauncher;
import tlm.dev.lectern.pipeline.publish.GitClient;
import tlm.dev.lectern.pipeline.publish.PublicationService;
import tlm.dev.lectern.pipeline.publish.RepositoryGuard;
import tlm.dev.lectern.pipeline.publish.TreeUpdater;

final class CommandRunner {

    static final int LIST_SIZE = 15;

    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration TRANSFER_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration MANIFEST_TRANSFER_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration GENERATOR_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration GIT_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration DIFF_TIMEOUT = Duration.ofMinutes(5);
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
            case Command.History _ -> history();
            case Command.Fetch fetch -> fetch(fetch.id());
            case Command.Extract extract -> extract(extract.id());
            case Command.Normalize normalize -> normalize(normalize.id());
            case Command.Publish publish -> publish(publish.id(), publish.dataRepository());
        }
    }

    private void list() {
        RecentVersionsService service = new RecentVersionsService(manifestClient(), new ManifestReader(new JsonTextReader()), scope());
        out.print(new VersionListRenderer().render(service.recent(LIST_SIZE)));
    }

    private void history() {
        HistoryService service = new HistoryService(manifestClient(), new ManifestReader(new JsonTextReader()), scope());
        out.print(new HistoryRenderer().render(service.history()));
    }

    private void fetch(VersionId id) {
        requireWorkbench();
        VersionPreparer preparer = preparer();
        out.print(new FetchReportRenderer().render(preparer.fetchJar(preparer.findVersion(id))));
    }

    private void extract(VersionId id) {
        requireWorkbench();
        ExtractionService extraction = extractionService();
        extraction.requireFreshOutput(id);
        VersionPreparer preparer = preparer(extraction);
        FetchedJar jar = preparer.fetchJar(preparer.findVersion(id));
        out.print(new FetchReportRenderer().render(jar));
        ExtractionResult result = extraction.extract(id, jar.jar());
        out.print(new ExtractionReportRenderer().render(result));
    }

    private void normalize(VersionId id) {
        requireWorkbench();
        preparer().normalizeExtracted(id);
    }

    private void publish(VersionId id, Path dataRepository) {
        requireWorkbench();
        new PublishCommand(publicationService(), preparer(), diffClient(), new TreeScanner(), out).run(id, dataRepository);
    }

    private VersionPreparer preparer() {
        return preparer(extractionService());
    }

    private VersionPreparer preparer(ExtractionService extraction) {
        JsonTextReader json = new JsonTextReader();
        return new VersionPreparer(
                config.workbench(),
                lookupService(),
                jarService(),
                extraction,
                normalizationService(),
                new VersionMetaReader(json),
                new GameInfoReader(json),
                out);
    }

    private VersionLookupService lookupService() {
        return new VersionLookupService(manifestClient(), new ManifestReader(new JsonTextReader()), scope());
    }

    private ServerJarService jarService() {
        return new ServerJarService(
                new HttpDownloadClient(http, RESPONSE_TIMEOUT, TRANSFER_TIMEOUT),
                new JarStore(config.workbench().resolve("jars")),
                new VersionJsonReader(new JsonTextReader()),
                new Sha1Hasher());
    }

    private ExtractionService extractionService() {
        ExtractionSettings settings = new ExtractionSettings(
                config.workbench().resolve("generated"), config.scratchRoot(), config.java(), GENERATOR_TIMEOUT);
        return new ExtractionService(launcher, new JarEraDetector(), settings, clock);
    }

    private NormalizationService normalizationService() {
        NormalizationTools tools = new NormalizationTools(new TreeScanner(), new JsonTextReader(), new CanonicalJsonWriter());
        NormalizationSettings settings = new NormalizationSettings(config.workbench().resolve("generated"), config.workbench().resolve("normalized"));
        return new NormalizationService(tools, new VersionMetaWriter(new CanonicalJsonWriter()), settings, clock);
    }

    private PublicationService publicationService() {
        GitClient git = new GitClient(launcher, config.git(), GIT_TIMEOUT);
        return new PublicationService(git, new RepositoryGuard(git), new TreeUpdater(), config.scratchRoot());
    }

    private Optional<DiffCommandClient> diffClient() {
        return config.diffCommand().map(script -> new DiffCommandClient(
                launcher,
                new DiffCommand(script, DIFF_TIMEOUT),
                new CanonicalOutputVerifier(new JsonTextReader(), new CanonicalJsonWriter()),
                config.scratchRoot()));
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

    private VersionScope scope() {
        return new VersionScope(excludedVersions(), Optional.of(config.historyStart()));
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
