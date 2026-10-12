package tlm.dev.lectern.pipeline.fixtures;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class RealDiffScript {

    private static final String VARIABLE = "LECTERN_DIFF_COMMAND";
    private static final Path SIBLING_SCRIPT = Path.of("LecternDiff", "build", "install", "LecternDiff", "bin", "LecternDiff");

    private RealDiffScript() {
    }

    public static Path require() {
        Path script = locate();
        assumeTrue(Files.isRegularFile(script), "Needs the LecternDiff script " + script
                + ". From the Lectern folder run: LecternDiff/gradlew -p LecternDiff installDist, or set " + VARIABLE + " to its bin script");
        return script;
    }

    public static void requireNormalized(Path workbench, List<String> ids) {
        for (String id : ids) {
            Path folder = workbench.resolve("normalized").resolve(id);
            assumeTrue(Files.isDirectory(folder), "Needs " + folder + ". From the Lectern folder run: LecternPipeline/gradlew -p LecternPipeline run --args=\"extract "
                    + id + "\" then --args=\"normalize " + id + "\"");
        }
    }

    private static Path locate() {
        String variable = System.getenv(VARIABLE);
        Path path = variable == null || variable.isBlank()
                ? Path.of("").toAbsolutePath().resolveSibling(SIBLING_SCRIPT.toString())
                : Path.of(variable).toAbsolutePath();
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        String name = path.getFileName().toString().toLowerCase();
        boolean launchable = name.endsWith(".bat") || name.endsWith(".cmd") || name.endsWith(".exe");
        return windows && !launchable ? path.resolveSibling(path.getFileName() + ".bat") : path;
    }
}
