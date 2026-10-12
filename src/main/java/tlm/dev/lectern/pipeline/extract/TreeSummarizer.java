package tlm.dev.lectern.pipeline.extract;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public final class TreeSummarizer {

    private static final String CACHE_FOLDER = ".cache";

    public TreeSummary summarize(Path root) {
        long[] counts = new long[4];
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile()) {
                        counts[0]++;
                        counts[1] += attributes.size();
                        if (!root.relativize(file).getName(0).toString().equals(CACHE_FOLDER)) {
                            counts[2]++;
                            counts[3] += attributes.size();
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not walk " + root, exception);
        }
        return new TreeSummary(counts[0], counts[1], counts[2], counts[3]);
    }
}
