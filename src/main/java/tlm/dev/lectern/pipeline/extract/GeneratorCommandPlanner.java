package tlm.dev.lectern.pipeline.extract;

import java.nio.file.Path;
import java.util.List;

public final class GeneratorCommandPlanner {

    private static final String DATA_MAIN_CLASS = "net.minecraft.data.Main";

    private GeneratorCommandPlanner() {
    }

    public static List<String> plan(JarEra era, Path java, Path jar, Path output) {
        return switch (era) {
            case JarEra.Bundler _ -> List.of(
                    java.toString(),
                    "-DbundlerMainClass=" + DATA_MAIN_CLASS,
                    "-jar", jar.toString(),
                    "--server", "--reports",
                    "--output", output.toString());
            case JarEra.Simple _ -> List.of(
                    java.toString(),
                    "-cp", jar.toString(),
                    DATA_MAIN_CLASS,
                    "--server", "--reports",
                    "--output", output.toString());
        };
    }
}
