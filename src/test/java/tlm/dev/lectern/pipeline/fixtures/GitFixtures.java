package tlm.dev.lectern.pipeline.fixtures;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tlm.dev.lectern.pipeline.publish.DataRepositoryRules;

public final class GitFixtures {

    public static final String NAME = "Demo Author";
    public static final String EMAIL = "demo@example.invalid";

    private GitFixtures() {
    }

    public static boolean gitAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static Path initRepository(Path folder) {
        return initRepository(folder, true);
    }

    public static Path initRepository(Path folder, boolean withAttributes) {
        try {
            Files.createDirectories(folder);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        run(folder, "init", "-q", "-b", "master");
        run(folder, "config", "--local", "user.name", NAME);
        run(folder, "config", "--local", "user.email", EMAIL);
        run(folder, "config", "--local", "core.autocrlf", "true");
        if (withAttributes) {
            TreeFixtures.write(folder, ".gitattributes", DataRepositoryRules.GITATTRIBUTES);
            run(folder, "add", ".gitattributes");
            run(folder, "commit", "-q", "-m", "chore: prepare the data repository");
        }
        return folder;
    }

    public static String run(Path repository, String... arguments) {
        List<String> command = new ArrayList<>(List.of("git", "-C", repository.toString()));
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                throw new IllegalStateException("git " + String.join(" ", arguments) + " failed: " + output);
            }
            return output;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    public static Path installHook(Path repository, String hook, String script) {
        Path file = repository.resolve(".git").resolve("hooks").resolve(hook);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, script, StandardCharsets.UTF_8);
            file.toFile().setExecutable(true);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return file;
    }
}
