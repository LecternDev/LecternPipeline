package tlm.dev.lectern.pipeline.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tlm.dev.lectern.pipeline.fixtures.FakeLauncher;
import tlm.dev.lectern.pipeline.process.ProcessLaunchException;
import tlm.dev.lectern.pipeline.process.ProcessOutcome;

class GitClientTest {

    private static final Path REPOSITORY = Path.of("data-repository");
    private static final Path WORK = Path.of("work");
    private static final Path GIT = Path.of("git");

    @Test
    void commandRunsGitOnTheRepositoryWithLiteralPathspecsFromAWorkFolderOutsideIt() {
        FakeLauncher launcher = new FakeLauncher(call -> new ProcessOutcome.Exited(0, "output\n", ""));
        String output = new GitClient(launcher, GIT, Duration.ofSeconds(30)).run(REPOSITORY, WORK, "add", "--", "versions/99.1");
        assertEquals("output\n", output);
        FakeLauncher.Call call = launcher.calls().get(0);
        assertEquals(List.of("git", "--literal-pathspecs", "-C", REPOSITORY.toString(), "add", "--", "versions/99.1"), call.command());
        assertEquals(WORK, call.workingDirectory());
        assertEquals(Duration.ofSeconds(30), call.timeout());
    }

    @Test
    void nonZeroExitIsAnErrorNamingTheArgumentsAndTheEndOfStderr() {
        FakeLauncher launcher = new FakeLauncher(call -> new ProcessOutcome.Exited(128, "", "line one\nfatal: invented failure"));
        GitException failure = assertThrows(GitException.class, () -> new GitClient(launcher, GIT, Duration.ofSeconds(30)).run(REPOSITORY, WORK, "commit", "-m", "x"));
        assertTrue(failure.getMessage().contains("git commit -m x"), failure.getMessage());
        assertTrue(failure.getMessage().contains("exited with code 128"), failure.getMessage());
        assertTrue(failure.getMessage().contains("fatal: invented failure"), failure.getMessage());
    }

    @Test
    void timeoutIsAnErrorNamingTheLimit() {
        FakeLauncher launcher = new FakeLauncher(call -> new ProcessOutcome.TimedOut(Duration.ofSeconds(30), "", ""));
        GitException failure = assertThrows(GitException.class, () -> new GitClient(launcher, GIT, Duration.ofSeconds(30)).run(REPOSITORY, WORK, "status"));
        assertTrue(failure.getMessage().contains("did not finish within 30 seconds"), failure.getMessage());
    }

    @Test
    void launcherThatCannotStartGitIsAnErrorWithTheCauseKept() {
        FakeLauncher launcher = new FakeLauncher(call -> {
            throw new ProcessLaunchException("Could not start git: not found", new IOException("not found"));
        });
        GitException failure = assertThrows(GitException.class, () -> new GitClient(launcher, GIT, Duration.ofSeconds(30)).run(REPOSITORY, WORK, "status"));
        assertTrue(failure.getMessage().contains("Could not run git status"), failure.getMessage());
        assertTrue(failure.getCause() instanceof ProcessLaunchException);
    }
}
