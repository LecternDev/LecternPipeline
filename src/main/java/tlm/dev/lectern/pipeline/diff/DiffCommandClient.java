package tlm.dev.lectern.pipeline.diff;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import tlm.dev.lectern.pipeline.files.FolderDeleter;
import tlm.dev.lectern.pipeline.process.ProcessLaunchException;
import tlm.dev.lectern.pipeline.process.ProcessLauncher;
import tlm.dev.lectern.pipeline.process.ProcessOutcome;

public final class DiffCommandClient {

    private static final System.Logger LOGGER = System.getLogger(DiffCommandClient.class.getName());
    private static final String WORK_FOLDER_PREFIX = "lectern-diff-";
    private static final int TAIL_LINES = 15;

    private final ProcessLauncher launcher;
    private final DiffCommand command;
    private final CanonicalOutputVerifier verifier;
    private final Path scratchRoot;

    public DiffCommandClient(ProcessLauncher launcher, DiffCommand command, CanonicalOutputVerifier verifier, Path scratchRoot) {
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.command = Objects.requireNonNull(command, "command");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.scratchRoot = Objects.requireNonNull(scratchRoot, "scratchRoot");
    }

    public void requireInstalled() {
        if (!Files.isRegularFile(command.executable())) {
            throw new DiffException("The diff command " + command.executable() + " does not exist. Build it with 'gradlew -p LecternDiff installDist'"
                    + " and give its bin script with --diff-command or the variable LECTERN_DIFF_COMMAND");
        }
    }

    public byte[] diff(DiffSide from, DiffSide to) {
        requireInstalled();
        requireFolder(to, from);
        requireFolder(to, to);
        Path work = newWorkFolder();
        try {
            return outputOf(from, to, launch(from, to, work));
        } finally {
            deleteWorkFolder(work);
        }
    }

    private ProcessOutcome launch(DiffSide from, DiffSide to, Path work) {
        List<String> line = List.of(
                command.executable().toString(), "diff", from.folder().toString(), to.folder().toString(),
                "--from", from.id().value(), "--from-kind", from.kind().label(),
                "--to", to.id().value(), "--to-kind", to.kind().label());
        try {
            return launcher.launch(line, work, command.timeout());
        } catch (ProcessLaunchException exception) {
            throw new DiffException("Version " + to.id().value() + ": could not run the diff command: " + exception.getMessage(), exception);
        }
    }

    private byte[] outputOf(DiffSide from, DiffSide to, ProcessOutcome outcome) {
        return switch (outcome) {
            case ProcessOutcome.Exited exited when exited.exitCode() == 0 -> verifier.verified(from, to, exited.stdout());
            case ProcessOutcome.Exited exited -> throw new DiffException("Version " + to.id().value() + ": the diff command exited with code "
                    + exited.exitCode() + " comparing it with " + from.id().value() + ". Nothing was written. Last lines of stderr:\n"
                    + ProcessOutcome.tail(exited.stderr(), TAIL_LINES));
            case ProcessOutcome.TimedOut timedOut -> throw new DiffException("Version " + to.id().value() + ": the diff command against "
                    + from.id().value() + " did not finish within " + timedOut.timeout().toSeconds() + " seconds and was stopped. Nothing was written");
        };
    }

    private static void requireFolder(DiffSide to, DiffSide side) {
        if (!Files.isDirectory(side.folder())) {
            throw new DiffException("Version " + to.id().value() + ": the normalized folder " + side.folder() + " of " + side.id().value()
                    + " does not exist, normalize it first");
        }
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
