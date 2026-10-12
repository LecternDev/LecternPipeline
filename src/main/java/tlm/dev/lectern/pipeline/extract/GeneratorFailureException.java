package tlm.dev.lectern.pipeline.extract;

import java.nio.file.Path;
import tlm.dev.lectern.pipeline.model.PipelineException;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class GeneratorFailureException extends PipelineException {

    private GeneratorFailureException(String message) {
        super(message);
    }

    private GeneratorFailureException(String message, Throwable cause) {
        super(message, cause);
    }

    public static GeneratorFailureException exited(VersionId id, Path output, ProcessOutcome.Exited outcome) {
        return new GeneratorFailureException("Version " + id.value() + ": the data generator exited with code " + outcome.exitCode()
                + ". The folder " + output + " was left as it is" + tails(outcome.stderrTail(), outcome.stdoutTail()));
    }

    public static GeneratorFailureException timedOut(VersionId id, Path output, ProcessOutcome.TimedOut outcome) {
        return new GeneratorFailureException("Version " + id.value() + ": the data generator did not finish within "
                + outcome.timeout().toSeconds() + " seconds and was stopped. The folder " + output + " was left as it is"
                + tails(outcome.stderrTail(), outcome.stdoutTail()));
    }

    public static GeneratorFailureException noOutput(VersionId id, Path output) {
        return new GeneratorFailureException("Version " + id.value() + ": the data generator exited with code 0 but wrote no folder " + output);
    }

    public static GeneratorFailureException notStarted(VersionId id, ProcessLaunchException cause) {
        return new GeneratorFailureException("Version " + id.value() + ": could not run the data generator: " + cause.getMessage(), cause);
    }

    private static String tails(String stderrTail, String stdoutTail) {
        return ".\nLast lines of stderr:\n" + indented(stderrTail) + "\nLast lines of stdout:\n" + indented(stdoutTail);
    }

    private static String indented(String text) {
        return text.isBlank() ? "  (empty)" : text.lines().map(line -> "  " + line).reduce((first, second) -> first + "\n" + second).orElse("  (empty)");
    }
}
