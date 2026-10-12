package tlm.dev.lectern.pipeline.process;

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
    private static final int DEFAULT_OUTPUT_LIMIT = 8 * 1024 * 1024;
    private static final String DROPPED_MARKER = "[earlier output dropped]\n";
    private static final Duration KILL_GRACE = Duration.ofSeconds(10);

    private final int outputLimit;

    public JdkProcessLauncher() {
        this(DEFAULT_OUTPUT_LIMIT);
    }

    public JdkProcessLauncher(int outputLimit) {
        if (outputLimit < 1) {
            throw new IllegalArgumentException("The output limit must be positive: " + outputLimit);
        }
        this.outputLimit = outputLimit;
    }

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
                return new ProcessOutcome.Exited(process.exitValue(), lastBytesOf(stdout), lastBytesOf(stderr));
            }
            process.destroyForcibly();
            process.waitFor(KILL_GRACE.toMillis(), TimeUnit.MILLISECONDS);
            return new ProcessOutcome.TimedOut(timeout, lastBytesOf(stdout), lastBytesOf(stderr));
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

    private String lastBytesOf(Path file) {
        try (SeekableByteChannel channel = Files.newByteChannel(file)) {
            long size = channel.size();
            long start = Math.max(0, size - outputLimit);
            channel.position(start);
            ByteBuffer buffer = ByteBuffer.allocate((int) (size - start));
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) {
                    break;
                }
            }
            String text = new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
            return start > 0 ? DROPPED_MARKER + text : text;
        } catch (IOException exception) {
            return "(output not readable: " + exception.getMessage() + ")";
        }
    }
}
