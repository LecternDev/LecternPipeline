package tlm.dev.lectern.pipeline;

import java.io.PrintStream;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import tlm.dev.lectern.pipeline.process.JdkProcessLauncher;
import tlm.dev.lectern.pipeline.process.ProcessLauncher;
import tlm.dev.lectern.pipeline.model.PipelineException;

public final class PipelineMain {

    private static final int EXIT_OK = 0;
    private static final int EXIT_FAILURE = 1;
    private static final int EXIT_USAGE = 2;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    private final PrintStream out;
    private final PrintStream err;
    private final Environment environment;
    private final ProcessLauncher launcher;
    private final Clock clock;

    PipelineMain(PrintStream out, PrintStream err, Environment environment, ProcessLauncher launcher, Clock clock) {
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static void main(String[] args) {
        PipelineMain main = new PipelineMain(System.out, System.err, Environment.ofThisProcess(), new JdkProcessLauncher(), Clock.systemUTC());
        System.exit(main.run(args));
    }

    int run(String[] args) {
        try {
            PipelineConfig config = new PipelineConfigReader(environment).read(args);
            try (HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()) {
                new CommandRunner(config, http, launcher, clock, out).run();
            }
            out.flush();
            return EXIT_OK;
        } catch (UsageException exception) {
            err.println(exception.getMessage());
            return EXIT_USAGE;
        } catch (PipelineException exception) {
            err.println("lectern-pipeline: " + exception.getMessage());
            return EXIT_FAILURE;
        }
    }
}
