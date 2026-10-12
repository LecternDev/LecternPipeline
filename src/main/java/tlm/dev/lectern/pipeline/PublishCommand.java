package tlm.dev.lectern.pipeline;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tlm.dev.lectern.pipeline.diff.DiffCommandClient;
import tlm.dev.lectern.pipeline.diff.DiffSide;
import tlm.dev.lectern.pipeline.manifest.DiffBase;
import tlm.dev.lectern.pipeline.manifest.VersionFormParser;
import tlm.dev.lectern.pipeline.manifest.VersionOutOfScopeException;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.model.VersionKind;
import tlm.dev.lectern.pipeline.normalize.DataAllowlist;
import tlm.dev.lectern.pipeline.normalize.TreeScanner;
import tlm.dev.lectern.pipeline.normalize.VersionMeta;
import tlm.dev.lectern.pipeline.publish.DataRepositoryRules;
import tlm.dev.lectern.pipeline.publish.PublicationReportRenderer;
import tlm.dev.lectern.pipeline.publish.PublicationRequest;
import tlm.dev.lectern.pipeline.publish.PublicationService;

final class PublishCommand {

    private final PublicationService publication;
    private final VersionPreparer preparer;
    private final Optional<DiffCommandClient> diff;
    private final TreeScanner scanner;
    private final PrintStream out;

    PublishCommand(PublicationService publication, VersionPreparer preparer, Optional<DiffCommandClient> diff, TreeScanner scanner, PrintStream out) {
        this.publication = publication;
        this.preparer = preparer;
        this.diff = diff;
        this.scanner = scanner;
        this.out = out;
    }

    void run(VersionId id, Path repository) {
        publication.requirePublishable(id, repository);
        Optional<DiffCommandClient> client = clientFor(id, preparer.diffBaseOf(id));
        Path normalized = preparer.ensureNormalized(id);
        VersionMeta meta = preparer.metaOf(id);
        List<String> files = scanner.scan(id, normalized, DataAllowlist.Layout.NORMALIZED);
        Map<String, byte[]> diffs = client.map(found -> diffs(meta, normalized, found)).orElse(Map.of());
        out.print(new PublicationReportRenderer().render(publication.publish(new PublicationRequest(id, normalized, files, diffs, repository))));
    }

    private Optional<DiffCommandClient> clientFor(VersionId id, DiffBase bases) {
        if (bases.step().isEmpty() && bases.release().isEmpty()) {
            return Optional.empty();
        }
        DiffCommandClient client = diff.orElseThrow(() -> new UsageException("Publishing " + id.value()
                + " needs the diff command, because it has a diff base. Give --diff-command <script> or set the variable "
                + Environment.DIFF_COMMAND_VARIABLE + " to the bin script produced by 'gradlew -p LecternDiff installDist'\n" + PipelineConfigReader.USAGE));
        client.requireInstalled();
        return Optional.of(client);
    }

    private Map<String, byte[]> diffs(VersionMeta meta, Path normalized, DiffCommandClient client) {
        Map<VersionId, byte[]> byBase = new HashMap<>();
        Map<String, byte[]> files = new LinkedHashMap<>();
        meta.stepBase().ifPresent(base -> files.put(DataRepositoryRules.STEP_DIFF_FILE,
                diffAgainst(DataRepositoryRules.STEP_DIFF_FILE, base, meta, normalized, client, byBase)));
        meta.releaseBase().ifPresent(base -> files.put(DataRepositoryRules.RELEASE_DIFF_FILE,
                diffAgainst(DataRepositoryRules.RELEASE_DIFF_FILE, base, meta, normalized, client, byBase)));
        return files;
    }

    private byte[] diffAgainst(String fileName, VersionId base, VersionMeta meta, Path normalized, DiffCommandClient client, Map<VersionId, byte[]> byBase) {
        byte[] bytes = byBase.computeIfAbsent(base, key -> {
            Path baseFolder = preparer.ensureNormalized(base);
            VersionKind baseKind = VersionFormParser.parse(base).orElseThrow(() -> VersionOutOfScopeException.unknownShape(base)).kind();
            return client.diff(new DiffSide(base, baseKind, baseFolder), new DiffSide(meta.id(), meta.kind(), normalized));
        });
        out.print("diff        " + fileName + " " + base.value() + " -> " + meta.id().value() + " (" + bytes.length + " bytes)\n");
        return bytes;
    }
}
