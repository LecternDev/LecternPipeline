package tlm.dev.lectern.pipeline.extract;

import java.nio.file.Path;
import tlm.dev.lectern.pipeline.model.PipelineException;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.process.ProcessLaunchException;
import tlm.dev.lectern.pipeline.process.ProcessOutcome;

public final class GeneratorFailureException extends PipelineException {

    private static final int TAIL_LINES = 15;

    private GeneratorFailureException(String message) {
        super(message);
    }

    private GeneratorFailureException(String message, Throwable cause) {
        super(message, cause);
    }

    public static GeneratorFailureException exited(VersionId id, Path output, ProcessOutcome.Exited outcome) {
        return new GeneratorFailureException("Version " + id.value() + ": the data generator exited with code " + outcome.exitCode()
                + ". The folder " + output + " was left as it is" + tails(outcome));
    }

    public static GeneratorFailureException timedOut(VersionId id, Path output, ProcessOutcome.TimedOut outcome) {
        return new GeneratorFailureException("Version " + id.value() + ": the data generator did not finish within "
                + outcome.timeout().toSeconds() + " seconds and was stopped. The folder " + output + " was left as it is"
                + tails(outcome));
    }

    public static GeneratorFailureException noOutput(VersionId id, Path output) {
        return new GeneratorFailureException("Version " + id.value() + ": the data generator exited with code 0 but wrote no folder " + output);
    }

    public static GeneratorFailureException notStarted(VersionId id, ProcessLaunchException cause) {
        return new GeneratorFailureException("Version " + id.value() + ": could not run the data generator: " + cause.getMessage(), cause);
    }

    private static String tails(ProcessOutcome outcome) {
        return ".\nLast lines of stderr:\n" + indented(ProcessOutcome.tail(outcome.stderr(), TAIL_LINES))
                + "\nLast lines of stdout:\n" + indented(ProcessOutcome.tail(outcome.stdout(), TAIL_LINES));
    }

    private static String indented(String text) {
        return text.isBlank() ? "  (empty)" : text.lines().map(line -> "  " + line).reduce((first, second) -> first + "\n" + second).orElse("  (empty)");
    }
}
