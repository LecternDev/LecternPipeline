package tlm.dev.lectern.pipeline;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import tlm.dev.lectern.pipeline.extract.ExtractionReportRenderer;
import tlm.dev.lectern.pipeline.extract.ExtractionResult;
import tlm.dev.lectern.pipeline.extract.ExtractionService;
import tlm.dev.lectern.pipeline.fetch.FetchReportRenderer;
import tlm.dev.lectern.pipeline.fetch.FetchedJar;
import tlm.dev.lectern.pipeline.fetch.ServerJarService;
import tlm.dev.lectern.pipeline.fetch.VersionJsonRef;
import tlm.dev.lectern.pipeline.manifest.DiffBase;
import tlm.dev.lectern.pipeline.manifest.LookedUpVersion;
import tlm.dev.lectern.pipeline.manifest.ManifestEntry;
import tlm.dev.lectern.pipeline.manifest.TrackedVersion;
import tlm.dev.lectern.pipeline.manifest.VersionLookupService;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.normalize.GameInfoReader;
import tlm.dev.lectern.pipeline.normalize.NormalizationReportRenderer;
import tlm.dev.lectern.pipeline.normalize.NormalizationResult;
import tlm.dev.lectern.pipeline.normalize.NormalizationService;
import tlm.dev.lectern.pipeline.normalize.VersionMeta;
import tlm.dev.lectern.pipeline.normalize.VersionMetaReader;

final class VersionPreparer {

    private static final String NORMALIZED_FOLDER = "normalized";
    private static final String GENERATED_FOLDER = "generated";

    private final Path workbench;
    private final VersionLookupService lookup;
    private final ServerJarService jars;
    private final ExtractionService extraction;
    private final NormalizationService normalization;
    private final VersionMetaReader metaReader;
    private final GameInfoReader gameInfo;
    private final PrintStream out;

    VersionPreparer(Path workbench, VersionLookupService lookup, ServerJarService jars, ExtractionService extraction,
            NormalizationService normalization, VersionMetaReader metaReader, GameInfoReader gameInfo, PrintStream out) {
        this.workbench = workbench;
        this.lookup = lookup;
        this.jars = jars;
        this.extraction = extraction;
        this.normalization = normalization;
        this.metaReader = metaReader;
        this.gameInfo = gameInfo;
        this.out = out;
    }

    TrackedVersion findVersion(VersionId id) {
        return lookup.find(id);
    }

    LookedUpVersion lookup(VersionId id) {
        return lookup.findWithBase(id);
    }

    FetchedJar fetchJar(TrackedVersion version) {
        ManifestEntry entry = version.entry();
        return jars.ensure(new VersionJsonRef(entry.id(), entry.url(), entry.sha1()));
    }

    void normalizeExtracted(VersionId id) {
        normalization.requireFreshOutput(id);
        LookedUpVersion looked = lookup(id);
        FetchedJar jar = fetchJar(looked.version());
        if (jar.downloaded()) {
            out.print(new FetchReportRenderer().render(jar));
        }
        normalize(looked, jar);
    }

    Path ensureNormalized(VersionId id) {
        Path normalized = normalizedFolder(id);
        if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            return normalized;
        }
        LookedUpVersion looked = lookup(id);
        FetchedJar jar = fetchJar(looked.version());
        if (jar.downloaded()) {
            out.print(new FetchReportRenderer().render(jar));
        }
        if (!Files.exists(workbench.resolve(GENERATED_FOLDER).resolve(id.value()), LinkOption.NOFOLLOW_LINKS)) {
            ExtractionResult extracted = extraction.extract(id, jar.jar());
            out.print(new ExtractionReportRenderer().render(extracted));
        }
        normalize(looked, jar);
        return normalized;
    }

    DiffBase diffBaseOf(VersionId id) {
        Path meta = normalizedFolder(id).resolve("meta.json");
        if (Files.isRegularFile(meta)) {
            VersionMeta read = metaReader.read(id, meta);
            return new DiffBase(read.stepBase(), read.releaseBase());
        }
        return lookup(id).base();
    }

    VersionMeta metaOf(VersionId id) {
        return metaReader.read(id, normalizedFolder(id).resolve("meta.json"));
    }

    Path normalizedFolder(VersionId id) {
        return workbench.resolve(NORMALIZED_FOLDER).resolve(id.value());
    }

    private void normalize(LookedUpVersion looked, FetchedJar jar) {
        TrackedVersion version = looked.version();
        VersionMeta meta = new VersionMeta(
                version.id(),
                version.kind(),
                version.releaseTime(),
                looked.base().step(),
                looked.base().release(),
                jar.download().sha1(),
                gameInfo.read(version.id(), jar.jar()));
        NormalizationResult result = normalization.normalize(meta);
        out.print(new NormalizationReportRenderer().render(result));
    }
}
