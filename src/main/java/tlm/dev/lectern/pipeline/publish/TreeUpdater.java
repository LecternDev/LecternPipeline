package tlm.dev.lectern.pipeline.publish;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

public final class TreeUpdater {

    public void applyRaw(Path repository, Path normalized, TreePlan plan) throws IOException {
        for (String relative : plan.removed()) {
            Path file = repository.resolve(relative);
            Files.deleteIfExists(file);
            pruneEmptyParents(repository, file);
        }
        for (String relative : plan.incoming()) {
            Path source = normalized.resolve(relative);
            Path destination = repository.resolve(relative);
            if (Files.isRegularFile(destination) && Files.mismatch(source, destination) == -1) {
                continue;
            }
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void copyFile(Path repository, Path source, String relative) throws IOException {
        Path destination = repository.resolve(relative);
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination);
    }

    public void writeFile(Path repository, String relative, byte[] bytes) throws IOException {
        Path destination = repository.resolve(relative);
        Files.createDirectories(destination.getParent());
        Files.write(destination, bytes);
    }

    public void remove(Path repository, Collection<String> relatives) throws IOException {
        for (String relative : relatives) {
            Path file = repository.resolve(relative);
            Files.deleteIfExists(file);
            pruneEmptyParents(repository, file);
        }
    }

    private static void pruneEmptyParents(Path repository, Path file) throws IOException {
        Path parent = file.getParent();
        while (parent != null && !parent.equals(repository) && parent.startsWith(repository)) {
            try {
                Files.deleteIfExists(parent);
            } catch (DirectoryNotEmptyException notEmpty) {
                return;
            }
            parent = parent.getParent();
        }
    }
}
