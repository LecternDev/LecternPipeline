package tlm.dev.lectern.pipeline.publish;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class RepositoryGuard {

    private static final int EXAMPLES = 5;
    private static final String TEXT_UNSET = ": text: unset";

    private final GitClient git;

    public RepositoryGuard(GitClient git) {
        this.git = Objects.requireNonNull(git, "git");
    }

    public void requirePublishable(VersionId id, Path repository, Path work) {
        requireRepositoryRoot(repository, work);
        requireCommit(repository, work);
        requireManagedFoldersAreFolders(repository);
        requireClean(repository, work);
        requireTextConversionOff(id, repository, work);
        requireVersionAbsent(id, repository, work);
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

    private void requireCommit(Path repository, Path work) {
        try {
            git.run(repository, work, "rev-parse", "-q", "--verify", "HEAD");
        } catch (GitException exception) {
            throw new PublicationException("The data repository " + repository + " has no commit yet. Commit its .gitattributes first, with these lines:\n"
                    + DataRepositoryRules.GITATTRIBUTES);
        }
    }

    private void requireManagedFoldersAreFolders(Path repository) {
        List<String> managed = new ArrayList<>(DataRepositoryRules.RAW_FOLDERS);
        managed.add(DataRepositoryRules.VERSIONS_FOLDER);
        managed.add(DataRepositoryRules.DIFFS_FOLDER);
        for (String name : managed) {
            Path folder = repository.resolve(name);
            if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            try {
                BasicFileAttributes attributes = Files.readAttributes(folder, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()) {
                    throw new PublicationException("The path " + folder + " in the data repository must be a plain folder, not a file or a link");
                }
            } catch (IOException exception) {
                throw new UncheckedIOException("Could not read the attributes of " + folder, exception);
            }
        }
    }

    private void requireClean(Path repository, Path work) {
        String status = git.run(repository, work, "status", "--porcelain", "-z", "--untracked-files=all");
        List<String> entries = new ArrayList<>();
        for (String entry : status.split("\0")) {
            if (!entry.isEmpty()) {
                entries.add(entry);
            }
        }
        if (!entries.isEmpty()) {
            throw new PublicationException("The data repository " + repository + " is not clean: " + entries.size() + " entries such as "
                    + entries.stream().limit(EXAMPLES).toList() + ". Nothing was changed, commit or discard them first");
        }
    }

    private void requireTextConversionOff(VersionId id, Path repository, Path work) {
        List<String> probes = DataRepositoryRules.attributeProbePaths(id.value());
        List<String> arguments = new ArrayList<>(List.of("check-attr", "text", "--"));
        arguments.addAll(probes);
        List<String> lines = git.run(repository, work, arguments.toArray(String[]::new)).lines().filter(line -> !line.isBlank()).toList();
        boolean allUnset = lines.size() == probes.size() && lines.stream().allMatch(line -> line.endsWith(TEXT_UNSET));
        if (!allUnset) {
            throw new PublicationException("The data repository " + repository + " does not switch text conversion off for reports/, data/,"
                    + " versions/ and diffs/, git could change the line endings of the published bytes. Commit a .gitattributes with these lines first:\n"
                    + DataRepositoryRules.GITATTRIBUTES);
        }
    }

    private void requireVersionAbsent(VersionId id, Path repository, Path work) {
        for (String folder : List.of(DataRepositoryRules.versionFolder(id.value()), DataRepositoryRules.diffsFolder(id.value()))) {
            if (Files.exists(repository.resolve(folder), LinkOption.NOFOLLOW_LINKS)) {
                throw new PublicationException("Version " + id.value() + ": the folder " + repository.resolve(folder)
                        + " already exists. Nothing was changed, this version looks published already");
            }
        }
        if (!git.run(repository, work, "tag", "--list", id.value()).isBlank()) {
            throw new PublicationException("Version " + id.value() + ": the tag " + id.value()
                    + " already exists in " + repository + ". Nothing was changed, this version looks published already");
        }
    }
}
