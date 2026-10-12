package tlm.dev.lectern.pipeline.fixtures;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class TreeFixtures {

    private TreeFixtures() {
    }

    public static void write(Path root, String relative, String text) {
        write(root, relative, text.getBytes(StandardCharsets.UTF_8));
    }

    public static void write(Path root, String relative, byte[] bytes) {
        Path file = root.resolve(relative);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static Path demoGeneratedTree(Path generatedRoot, String id) {
        Path root = generatedRoot.resolve(id);
        write(root, "reports/gadgets.json", "{\"demo:lamp\": {\"protocol_id\": 1}, \"demo:chest\": {\"protocol_id\": 0}}");
        write(root, "reports/minecraft/components/item/demo_lamp.json", "{\"components\": {\"demo:b\": 1.0, \"demo:a\": []}}");
        write(root, "data/demo/recipe/thing.json", "{\r\n  \"b\": 1,\r\n  \"a\": 0.50\r\n}\r\n");
        write(root, "data/minecraft/datapacks/demo_pack/pack.mcmeta", "{\"pack\": {\"description\": \"demo\"}}");
        write(root, ".cache/0123abcd", "// demo cache with a run timestamp, never published\n");
        write(root, ".cache/nested/blob.nbt", new byte[] {0, 1, 2, 3});
        try {
            Files.createDirectories(root.resolve("tmp"));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return root;
    }

    public static List<String> filesUnder(Path root) {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).map(path -> root.relativize(path).toString().replace('\\', '/')).sorted().toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static boolean trySymbolicLink(Path link, Path target) throws IOException {
        try {
            Files.createDirectories(link.getParent());
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | SecurityException | IOException exception) {
            return false;
        }
    }

    public static boolean tryFolderLink(Path link, Path target) throws IOException {
        if (trySymbolicLink(link, target)) {
            return true;
        }
        if (!System.getProperty("os.name").startsWith("Windows")) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString()).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0 && Files.exists(link);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
