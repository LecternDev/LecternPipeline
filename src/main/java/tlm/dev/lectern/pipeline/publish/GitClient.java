package tlm.dev.lectern.pipeline.publish;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tlm.dev.lectern.pipeline.process.ProcessLaunchException;
import tlm.dev.lectern.pipeline.process.ProcessLauncher;
import tlm.dev.lectern.pipeline.process.ProcessOutcome;

public final class GitClient {

    private static final int TAIL_LINES = 15;

    private final ProcessLauncher launcher;
    private final Path git;
    private final Duration timeout;

    public GitClient(ProcessLauncher launcher, Path git, Duration timeout) {
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.git = Objects.requireNonNull(git, "git");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    public String run(Path repository, Path workFolder, String... arguments) {
        List<String> command = new ArrayList<>(List.of(git.toString(), "--literal-pathspecs", "-C", repository.toString()));
        command.addAll(List.of(arguments));
        String shown = String.join(" ", arguments);
        ProcessOutcome outcome;
        try {
            outcome = launcher.launch(command, workFolder, timeout);
        } catch (ProcessLaunchException exception) {
            throw new GitException("Could not run git " + shown + ": " + exception.getMessage(), exception);
        }
        return switch (outcome) {
            case ProcessOutcome.Exited exited when exited.exitCode() == 0 -> exited.stdout();
            case ProcessOutcome.Exited exited -> throw new GitException("git " + shown + " exited with code " + exited.exitCode()
                    + ": " + ProcessOutcome.tail(exited.stderr(), TAIL_LINES));
            case ProcessOutcome.TimedOut timedOut -> throw new GitException("git " + shown + " did not finish within "
                    + timedOut.timeout().toSeconds() + " seconds and was stopped");
        };
    }
}
