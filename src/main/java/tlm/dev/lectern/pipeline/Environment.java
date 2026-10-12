package tlm.dev.lectern.pipeline;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record Environment(Path currentDirectory, Path javaHome, Path tempDirectory, boolean windows, Optional<String> diffCommand) {

    static final String DIFF_COMMAND_VARIABLE = "LECTERN_DIFF_COMMAND";

    public Environment {
        Objects.requireNonNull(currentDirectory, "currentDirectory");
        Objects.requireNonNull(javaHome, "javaHome");
        Objects.requireNonNull(tempDirectory, "tempDirectory");
        Objects.requireNonNull(diffCommand, "diffCommand");
    }

    public static Environment ofThisProcess() {
        return new Environment(
                Path.of("").toAbsolutePath(),
                Path.of(System.getProperty("java.home")),
                Path.of(System.getProperty("java.io.tmpdir")),
                System.getProperty("os.name").startsWith("Windows"),
                Optional.ofNullable(System.getenv(DIFF_COMMAND_VARIABLE)).filter(value -> !value.isBlank()));
    }
}
