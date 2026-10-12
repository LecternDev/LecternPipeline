package tlm.dev.lectern.pipeline.fixtures;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import tlm.dev.lectern.pipeline.extract.ProcessLauncher;
import tlm.dev.lectern.pipeline.extract.ProcessOutcome;

public final class FakeLauncher implements ProcessLauncher {

    public record Call(List<String> command, Path workingDirectory, Duration timeout, boolean workingDirectoryExisted) {
    }

    private final List<Call> calls = new ArrayList<>();
    private final Function<Call, ProcessOutcome> behaviour;

    public FakeLauncher(Function<Call, ProcessOutcome> behaviour) {
        this.behaviour = behaviour;
    }

    public static FakeLauncher writingFiles(String... relativeFiles) {
        return new FakeLauncher(call -> {
            Path output = outputOf(call.command());
            for (String relative : relativeFiles) {
                write(output.resolve(relative), relative.length());
            }
            return new ProcessOutcome.Exited(0, "", "");
        });
    }

    public static Path outputOf(List<String> command) {
        return Path.of(command.get(command.indexOf("--output") + 1));
    }

    public static void write(Path file, int bytes) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, new byte[bytes]);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Override
    public ProcessOutcome launch(List<String> command, Path workingDirectory, Duration timeout) {
        Call call = new Call(List.copyOf(command), workingDirectory, timeout, Files.isDirectory(workingDirectory));
        calls.add(call);
        return behaviour.apply(call);
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }
}
