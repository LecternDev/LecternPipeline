package tlm.dev.lectern.pipeline;

import java.nio.file.Path;
import java.util.Objects;

public record Environment(Path currentDirectory, Path javaHome, Path tempDirectory) {

    public Environment {
        Objects.requireNonNull(currentDirectory, "currentDirectory");
        Objects.requireNonNull(javaHome, "javaHome");
        Objects.requireNonNull(tempDirectory, "tempDirectory");
    }

    public static Environment ofThisProcess() {
        return new Environment(
                Path.of("").toAbsolutePath(),
                Path.of(System.getProperty("java.home")),
                Path.of(System.getProperty("java.io.tmpdir")));
    }
}
