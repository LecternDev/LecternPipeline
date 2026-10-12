package tlm.dev.lectern.pipeline.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TreeSummarizerTest {

    private final TreeSummarizer summarizer = new TreeSummarizer();

    private static void file(Path path, int bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[bytes]);
    }

    @Test
    void filesAndBytesAreCountedWithAndWithoutTheCacheFolder(@TempDir Path root) throws IOException {
        file(root.resolve("reports/blocks.json"), 100);
        file(root.resolve("reports/minecraft/components/item/a.json"), 20);
        file(root.resolve("data/minecraft/recipe/b.json"), 3);
        file(root.resolve(".cache/abc"), 7);
        file(root.resolve(".cache/def"), 5);
        assertEquals(new TreeSummary(5, 135, 3, 123), summarizer.summarize(root));
    }

    @Test
    void onlyTheTopLevelCacheFolderIsExcluded(@TempDir Path root) throws IOException {
        file(root.resolve("data/.cache/x"), 4);
        file(root.resolve("reports/y.json"), 6);
        assertEquals(new TreeSummary(2, 10, 2, 10), summarizer.summarize(root));
    }

    @Test
    void emptyFolderGivesZeros(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("empty/inside"));
        assertEquals(new TreeSummary(0, 0, 0, 0), summarizer.summarize(root));
    }
}
