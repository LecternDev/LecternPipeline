package tlm.dev.lectern.pipeline.process;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface ProcessLauncher {

    ProcessOutcome launch(List<String> command, Path workingDirectory, Duration timeout);
}
