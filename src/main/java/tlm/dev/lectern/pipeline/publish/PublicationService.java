package tlm.dev.lectern.pipeline.publish;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import tlm.dev.lectern.pipeline.files.DraftFolders;
import tlm.dev.lectern.pipeline.files.FolderDeleter;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class PublicationService {

    private static final System.Logger LOGGER = System.getLogger(PublicationService.class.getName());
    private static final String WORK_FOLDER_PREFIX = "lectern-publish-";
    private static final int EXAMPLES = 5;

    private final GitClient git;
    private final Path scratchRoot;

    public PublicationService(GitClient git, Path scratchRoot) {
        this.git = Objects.requireNonNull(git, "git");
        this.scratchRoot = Objects.requireNonNull(scratchRoot, "scratchRoot");
    }

    public void requirePublishable(VersionId id, Path repository) {
        Path work = newWorkFolder();
        try {
            requireUsable(id, repository, work);
        } finally {
            deleteWorkFolder(work);
        }
    }

    public PublicationResult publish(VersionId id, Path source, List<String> files, Path repository) {
        Path work = newWorkFolder();
        try {
            requireUsable(id, repository, work);
            Path target = repository.resolve(DataRepositoryRules.versionFolder(id.value()));
            Set<String> expected = copyInto(id, source, files, target);
            stageAndCommit(id, repository, work, expected);
            return new PublicationResult(id, repository, files.size(), git.run(repository, work, "rev-parse", "HEAD").strip());
        } finally {
            deleteWorkFolder(work);
        }
    }

    private void requireUsable(VersionId id, Path repository, Path work) {
        requireRepositoryRoot(repository, work);
        requireTextConversionOff(id, repository, work);
        Path target = repository.resolve(DataRepositoryRules.versionFolder(id.value()));
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new PublicationException("Version " + id.value() + ": the folder " + target
                    + " already exists. Nothing was changed, remove it yourself to publish again");
        }
        requireNothingStaged(repository, work);
    }

    private void requireRepositoryRoot(Path repository, Path work) {
        if (!Files.isDirectory(repository)) {
            throw new PublicationException("The data repository " + repository + " is not a folder");
        }
        String top;
        try {
            top = git.run(repository, work, "rev-parse", "--show-toplevel").strip();
        } catch (GitException exception) {
            throw new PublicationException("The folder " + repository + " is not a git repository (" + exception.getMessage() + ")");
        }
        try {
            if (top.isEmpty() || !Files.isSameFile(Path.of(top), repository)) {
                throw new PublicationException("The folder " + repository + " is not the top level of a git repository, which is " + top);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not compare " + repository + " with " + top, exception);
        }
    }

    private void requireTextConversionOff(VersionId id, Path repository, Path work) {
        String attribute = git.run(repository, work, "check-attr", "text", "--", DataRepositoryRules.versionFolder(id.value()) + "/meta.json");
        if (!attribute.strip().endsWith(": text: unset")) {
            throw new PublicationException("The data repository " + repository + " does not switch text conversion off for versions/,"
                    + " git could change the line endings of the published bytes. Commit a .gitattributes with these lines first:\n"
                    + DataRepositoryRules.GITATTRIBUTES);
        }
    }

    private void requireNothingStaged(Path repository, Path work) {
        if (!stagedPaths(repository, work).isEmpty()) {
            throw new PublicationException("The data repository " + repository + " already has staged changes. Nothing was changed,"
                    + " commit or unstage them first");
        }
    }

    private Set<String> copyInto(VersionId id, Path source, List<String> files, Path target) {
        Path draft = DraftFolders.draftOf(target);
        try {
            DraftFolders.clear(draft);
            Set<String> published = new HashSet<>();
            for (String relative : files) {
                requirePlainRelativePath(id, relative);
                Path destination = draft.resolve(relative);
                Files.createDirectories(destination.getParent());
                Files.copy(source.resolve(relative), destination);
                published.add(DataRepositoryRules.versionFolder(id.value()) + "/" + relative);
            }
            DraftFolders.promote(draft, target);
            return published;
        } catch (FileAlreadyExistsException exception) {
            throw new PublicationException("Version " + id.value() + ": " + exception.getFile() + " appeared during the run and was not replaced");
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not copy version " + id.value() + " into " + target, exception);
        }
    }

    private static void requirePlainRelativePath(VersionId id, String relative) {
        boolean plain = !relative.isEmpty() && !relative.startsWith("/") && relative.indexOf('\\') < 0 && relative.indexOf(':') < 0
                && List.of(relative.split("/", -1)).stream().noneMatch(segment -> segment.isEmpty() || segment.equals(".") || segment.equals(".."));
        if (!plain) {
            throw new PublicationException("Version " + id.value() + ": the path '" + relative + "' is not a plain relative path");
        }
    }

    private void stageAndCommit(VersionId id, Path repository, Path work, Set<String> expected) {
        String folder = DataRepositoryRules.versionFolder(id.value());
        try {
            git.run(repository, work, "add", "--", folder);
            requireSamePaths(id, "staged", expected, stagedPaths(repository, work));
            git.run(repository, work, "commit", "-q", "-m", "data: " + id.value());
        } catch (GitException | PublicationException exception) {
            withdraw(repository, work, folder, exception);
            throw exception;
        }
        requireOneLineMessage(id, repository, work);
        requireSamePaths(id, "committed", expected,
                splitNul(git.run(repository, work, "diff-tree", "--root", "--no-commit-id", "--name-only", "-r", "-z", "HEAD")));
    }

    private void withdraw(Path repository, Path work, String folder, RuntimeException failure) {
        try {
            git.run(repository, work, "reset", "-q", "--", folder);
        } catch (GitException resetFailure) {
            failure.addSuppressed(resetFailure);
            return;
        }
        try {
            FolderDeleter.deleteTree(repository.resolve(folder));
        } catch (IOException deleteFailure) {
            failure.addSuppressed(deleteFailure);
        }
    }

    private void requireOneLineMessage(VersionId id, Path repository, Path work) {
        String message = git.run(repository, work, "log", "-1", "--pretty=%B").strip();
        if (!message.equals("data: " + id.value())) {
            throw new PublicationException("Version " + id.value() + ": the commit message is not the single line 'data: " + id.value()
                    + "', a hook may have changed it. Message found: [" + message + "]. The commit was made, fix it by hand");
        }
    }

    private static void requireSamePaths(VersionId id, String what, Set<String> expected, Set<String> found) {
        if (expected.equals(found)) {
            return;
        }
        List<String> unexpected = new ArrayList<>(found);
        unexpected.removeAll(expected);
        List<String> missing = new ArrayList<>(expected);
        missing.removeAll(found);
        throw new PublicationException("Version " + id.value() + ": the " + what + " files are not exactly the published ones. Unexpected "
                + unexpected.size() + " " + unexpected.stream().sorted().limit(EXAMPLES).toList()
                + ", missing " + missing.size() + " " + missing.stream().sorted().limit(EXAMPLES).toList());
    }

    private Set<String> stagedPaths(Path repository, Path work) {
        return splitNul(git.run(repository, work, "diff", "--cached", "--name-only", "--no-renames", "-z"));
    }

    private static Set<String> splitNul(String output) {
        Set<String> paths = new HashSet<>();
        for (String path : output.split("\0")) {
            if (!path.isEmpty()) {
                paths.add(path);
            }
        }
        return paths;
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
