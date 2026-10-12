package tlm.dev.lectern.pipeline.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class DraftFolders {

    private static final String SUFFIX = ".partial";

    private DraftFolders() {
    }

    public static Path draftOf(Path target) {
        return target.resolveSibling(target.getFileName() + SUFFIX);
    }

    public static void clear(Path draft) throws IOException {
        if (!Files.exists(draft, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isDirectory(draft, LinkOption.NOFOLLOW_LINKS)) {
            FolderDeleter.deleteTree(draft);
        } else {
            Files.delete(draft);
        }
    }

    public static void promote(Path draft, Path target) throws IOException {
        Files.move(draft, target);
    }
}
