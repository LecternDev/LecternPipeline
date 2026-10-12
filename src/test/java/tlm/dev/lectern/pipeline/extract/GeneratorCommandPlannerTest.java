package tlm.dev.lectern.pipeline.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeneratorCommandPlannerTest {

    private static final Path JAVA = Path.of("jdk", "bin", "java");
    private static final Path JAR = Path.of("jars", "99.1", "server.jar");
    private static final Path OUTPUT = Path.of("generated", "99.1");

    @Test
    void bundlerJarIsRunWithTheBundlerMainClassPropertyAndJarOption() {
        assertEquals(
                List.of(JAVA.toString(), "-DbundlerMainClass=net.minecraft.data.Main", "-jar", JAR.toString(),
                        "--server", "--reports", "--output", OUTPUT.toString()),
                GeneratorCommandPlanner.plan(new JarEra.Bundler(), JAVA, JAR, OUTPUT));
    }

    @Test
    void simpleJarIsRunOnTheClassPathWithTheDataGeneratorMainClass() {
        assertEquals(
                List.of(JAVA.toString(), "-cp", JAR.toString(), "net.minecraft.data.Main",
                        "--server", "--reports", "--output", OUTPUT.toString()),
                GeneratorCommandPlanner.plan(new JarEra.Simple(), JAVA, JAR, OUTPUT));
    }
}
