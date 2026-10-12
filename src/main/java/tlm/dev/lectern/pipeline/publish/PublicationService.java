package tlm.dev.lectern.pipeline.publish;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import tlm.dev.lectern.pipeline.files.FolderDeleter;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class PublicationService {

    private static final System.Logger LOGGER = System.getLogger(PublicationService.class.getName());
    private static final String WORK_FOLDER_PREFIX = "lectern-publish-";

    private final GitClient git;
    private final RepositoryGuard guard;
    private final TreeUpdater updater;
    private final Path scratchRoot;

    public PublicationService(GitClient git, RepositoryGuard guard, TreeUpdater updater, Path scratchRoot) {
        this.git = Objects.requireNonNull(git, "git");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.updater = Objects.requireNonNull(updater, "updater");
        this.scratchRoot = Objects.requireNonNull(scratchRoot, "scratchRoot");
    }

    public void requirePublishable(VersionId id, Path repository) {
        Path work = newWorkFolder();
        try {
            guard.requirePublishable(id, repository, work);
        } finally {
            deleteWorkFolder(work);
        }
    }

    public PublicationResult publish(PublicationRequest request) {
        Path work = newWorkFolder();
        try {
            return publishIn(request, work);
        } finally {
            deleteWorkFolder(work);
        }
    }

    private PublicationResult publishIn(PublicationRequest request, Path work) {
        VersionId id = request.id();
        Path repository = request.repository();
        guard.requirePublishable(id, repository, work);
        requireKnownShape(request);
        TreePlan plan = new TreePlan(Set.copyOf(request.rawFiles()), trackedRawFiles(repository, work));
        Set<String> extra = extraPaths(request);
        StagedSet staged = stageAndCommit(request, plan, extra, work);
        requireOneLineMessage(id, repository, work);
        StagedSetVerifier.verifySame(id, staged, committedSet(repository, work));
        String commit = git.run(repository, work, "rev-parse", "HEAD").strip();
        createTag(id, repository, work, commit);
        return new PublicationResult(id, repository, commit, plan.incoming().size(), rawChanges(staged), diffNames(request));
    }

    private StagedSet stageAndCommit(PublicationRequest request, TreePlan plan, Set<String> extra, Path work) {
        VersionId id = request.id();
        Path repository = request.repository();
        try {
            writeFiles(request, plan);
            git.run(repository, work, stageArguments(request, plan));
            StagedSet staged = StagedSet.parse(git.run(repository, work, "diff", "--cached", "--name-status", "--no-renames", "-z"));
            StagedSetVerifier.verify(id, "staged", plan, extra, staged);
            git.run(repository, work, "commit", "-q", "-m", DataRepositoryRules.commitTitle(id.value()));
            return staged;
        } catch (RuntimeException failure) {
            Set<String> created = new HashSet<>(plan.added());
            created.addAll(extra);
            restore(repository, work, created, failure);
            throw failure;
        }
    }

    private void writeFiles(PublicationRequest request, TreePlan plan) {
        VersionId id = request.id();
        try {
            updater.applyRaw(request.repository(), request.normalizedFolder(), plan);
            updater.copyFile(request.repository(), request.normalizedFolder().resolve(DataRepositoryRules.META_FILE), DataRepositoryRules.metaPath(id.value()));
            for (String name : DataRepositoryRules.DIFF_FILES) {
                byte[] bytes = request.diffs().get(name);
                if (bytes != null) {
                    updater.writeFile(request.repository(), DataRepositoryRules.diffPath(id.value(), name), bytes);
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write version " + id.value() + " into " + request.repository(), exception);
        }
    }

    private static String[] stageArguments(PublicationRequest request, TreePlan plan) {
        List<String> arguments = new ArrayList<>(List.of("add", "--"));
        for (String folder : DataRepositoryRules.RAW_FOLDERS) {
            boolean written = request.rawFiles().stream().anyMatch(file -> file.startsWith(folder + "/"));
            boolean emptied = plan.removed().stream().anyMatch(file -> file.startsWith(folder + "/"));
            if (written || emptied) {
                arguments.add(folder);
            }
        }
        arguments.add(DataRepositoryRules.versionFolder(request.id().value()));
        if (!request.diffs().isEmpty()) {
            arguments.add(DataRepositoryRules.diffsFolder(request.id().value()));
        }
        return arguments.toArray(String[]::new);
    }

    private void restore(Path repository, Path work, Set<String> created, RuntimeException failure) {
        try {
            git.run(repository, work, "reset", "-q", "--hard", "HEAD");
        } catch (GitException resetFailure) {
            failure.addSuppressed(resetFailure);
            return;
        }
        try {
            updater.remove(repository, created);
        } catch (IOException removeFailure) {
            failure.addSuppressed(removeFailure);
        }
    }

    private void requireOneLineMessage(VersionId id, Path repository, Path work) {
        String expected = DataRepositoryRules.commitTitle(id.value());
        String message = git.run(repository, work, "log", "-1", "--pretty=%B").strip();
        if (!message.equals(expected)) {
            throw new PublicationException("Version " + id.value() + ": the commit message is not the single line '" + expected
                    + "', a hook may have changed it. Message found: [" + message + "]. The commit was made, fix it by hand and create the tag "
                    + id.value());
        }
    }

    private StagedSet committedSet(Path repository, Path work) {
        return StagedSet.parse(git.run(repository, work, "diff-tree", "-r", "--no-commit-id", "--name-status", "--no-renames", "-z", "HEAD"));
    }

    private void createTag(VersionId id, Path repository, Path work, String commit) {
        try {
            git.run(repository, work, "tag", "--", id.value());
        } catch (GitException failure) {
            throw new PublicationException("Version " + id.value() + ": the commit " + commit + " was made but the tag " + id.value()
                    + " could not be created (" + failure.getMessage() + "). Create it by hand with: git tag " + id.value() + " " + commit, failure);
        }
    }

    private Set<String> trackedRawFiles(Path repository, Path work) {
        List<String> arguments = new ArrayList<>(List.of("ls-files", "-z", "--"));
        arguments.addAll(DataRepositoryRules.RAW_FOLDERS);
        Set<String> tracked = new HashSet<>();
        for (String path : git.run(repository, work, arguments.toArray(String[]::new)).split("\0")) {
            if (!path.isEmpty()) {
                tracked.add(path);
            }
        }
        return tracked;
    }

    private static Set<String> extraPaths(PublicationRequest request) {
        Set<String> extra = new HashSet<>();
        extra.add(DataRepositoryRules.metaPath(request.id().value()));
        for (String name : request.diffs().keySet()) {
            extra.add(DataRepositoryRules.diffPath(request.id().value(), name));
        }
        return extra;
    }

    private static List<String> diffNames(PublicationRequest request) {
        return DataRepositoryRules.DIFF_FILES.stream().filter(request.diffs()::containsKey).toList();
    }

    private static TreeChanges rawChanges(StagedSet staged) {
        return new TreeChanges(rawCount(staged, StagedSetVerifier.ADDED), rawCount(staged, StagedSetVerifier.MODIFIED), rawCount(staged, StagedSetVerifier.DELETED));
    }

    private static int rawCount(StagedSet staged, String status) {
        return DataRepositoryRules.RAW_FOLDERS.stream().mapToInt(folder -> staged.count(status, folder + "/")).sum();
    }

    private static void requireKnownShape(PublicationRequest request) {
        VersionId id = request.id();
        if (!request.hasMeta()) {
            throw new PublicationException("Version " + id.value() + ": the normalized folder " + request.normalizedFolder() + " has no "
                    + DataRepositoryRules.META_FILE);
        }
        for (String relative : request.files()) {
            boolean known = relative.equals(DataRepositoryRules.META_FILE)
                    || DataRepositoryRules.RAW_FOLDERS.stream().anyMatch(folder -> relative.startsWith(folder + "/"));
            if (!known || !isPlainRelativePath(relative)) {
                throw new PublicationException("Version " + id.value() + ": the path '" + relative + "' is not a plain relative path under reports/ or data/");
            }
        }
    }

    private static boolean isPlainRelativePath(String relative) {
        return !relative.isEmpty() && !relative.startsWith("/") && relative.indexOf('\\') < 0 && relative.indexOf(':') < 0
                && List.of(relative.split("/", -1)).stream().noneMatch(segment -> segment.isEmpty() || segment.equals(".") || segment.equals(".."));
    }

    private Path newWorkFolder() {
        try {
            Files.createDirectories(scratchRoot);
            return Files.createTempDirectory(scratchRoot, WORK_FOLDER_PREFIX);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not create a work folder under " + scratchRoot, exception);
        }
    }

    private static void deleteWorkFolder(Path folder) {
        try {
            FolderDeleter.deleteTree(folder);
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not delete the work folder " + folder + ": " + exception);
        }
    }
}
