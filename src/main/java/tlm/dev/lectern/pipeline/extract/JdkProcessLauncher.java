package tlm.dev.lectern.pipeline.extract;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class JdkProcessLauncher implements ProcessLauncher {

    private static final String STDOUT_LOG = "lectern-stdout.log";
    private static final String STDERR_LOG = "lectern-stderr.log";
    private static final int TAIL_LINES = 15;
    private static final int TAIL_BYTES = 64 * 1024;
    private static final Duration KILL_GRACE = Duration.ofSeconds(10);

    @Override
    public ProcessOutcome launch(List<String> command, Path workingDirectory, Duration timeout) {
        Path stdout = workingDirectory.resolve(STDOUT_LOG);
        Path stderr = workingDirectory.resolve(STDERR_LOG);
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile());
        Process process = start(builder, command);
        try {
            process.getOutputStream().close();
            if (process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return new ProcessOutcome.Exited(process.exitValue(), tail(stdout), tail(stderr));
            }
            process.destroyForcibly();
            process.waitFor(KILL_GRACE.toMillis(), TimeUnit.MILLISECONDS);
            return new ProcessOutcome.TimedOut(timeout, tail(stdout), tail(stderr));
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new ProcessLaunchException("Interrupted while waiting for " + command.get(0), exception);
        } catch (IOException exception) {
            process.destroyForcibly();
            throw new ProcessLaunchException("Could not talk to " + command.get(0) + ": " + exception.getMessage(), exception);
        }
    }

    private static Process start(ProcessBuilder builder, List<String> command) {
        try {
            return builder.start();
        } catch (IOException exception) {
            throw new ProcessLaunchException("Could not start " + command.get(0) + ": " + exception.getMessage(), exception);
        }
    }

    private static String tail(Path file) {
        try (SeekableByteChannel channel = Files.newByteChannel(file)) {
            long size = channel.size();
            long start = Math.max(0, size - TAIL_BYTES);
            channel.position(start);
            ByteBuffer buffer = ByteBuffer.allocate((int) (size - start));
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) {
                    break;
                }
            }
            List<String> lines = new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8).lines().toList();
            return String.join("\n", lines.subList(Math.max(0, lines.size() - TAIL_LINES), lines.size()));
        } catch (IOException exception) {
            return "(output not readable: " + exception.getMessage() + ")";
        }
    }
}
