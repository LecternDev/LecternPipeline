package tlm.dev.lectern.pipeline.fetch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class JarStore {

    private static final String JAR_NAME = "server.jar";
    private static final String PART_PREFIX = "server-";
    private static final String PART_SUFFIX = ".jar.part";

    private final Path jarsRoot;

    public JarStore(Path jarsRoot) {
        this.jarsRoot = Objects.requireNonNull(jarsRoot, "jarsRoot");
    }

    public Path jarPath(VersionId id) {
        return directoryOf(id).resolve(JAR_NAME);
    }

    public boolean hasJar(VersionId id) {
        return Files.exists(jarPath(id));
    }

    public long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the size of " + file, exception);
        }
    }

    public Path newPartFile(VersionId id) {
        Path directory = directoryOf(id);
        try {
            Files.createDirectories(directory);
            return Files.createTempFile(directory, PART_PREFIX, PART_SUFFIX);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not create a temporary file in " + directory, exception);
        }
    }

    public void publish(Path part, VersionId id) {
        Path jar = jarPath(id);
        try {
            Files.move(part, jar);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not move " + part + " to " + jar, exception);
        }
    }

    public void discard(Path part) {
        try {
            Files.deleteIfExists(part);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not delete the temporary file " + part, exception);
        }
    }

    private Path directoryOf(VersionId id) {
        return jarsRoot.resolve(id.value());
    }
}
