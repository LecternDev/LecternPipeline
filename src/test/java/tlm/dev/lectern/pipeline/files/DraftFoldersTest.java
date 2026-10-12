package tlm.dev.lectern.pipeline.files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DraftFoldersTest {

    @TempDir
    Path root;

    private static void file(Path path, int bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[bytes]);
    }

    @Test
    void draftIsTheSiblingWithThePartialSuffix() {
        assertEquals(root.resolve("26.3.partial"), DraftFolders.draftOf(root.resolve("26.3")));
    }

    @Test
    void clearRemovesAFolderTreeAMissingPathIsNotAnError() throws IOException {
        Path draft = root.resolve("x.partial");
        file(draft.resolve("a/b/c.json"), 3);
        file(draft.resolve("d.json"), 2);
        DraftFolders.clear(draft);
        assertFalse(Files.exists(draft));
        DraftFolders.clear(draft);
    }

    @Test
    void clearRemovesAPlainFileToo() throws IOException {
        Path draft = root.resolve("x.partial");
        Files.write(draft, new byte[] {1});
        DraftFolders.clear(draft);
        assertFalse(Files.exists(draft));
    }

    @Test
    void clearLeavesTheSiblingFinalFolderAlone() throws IOException {
        file(root.resolve("x/keep.json"), 4);
        file(root.resolve("x.partial/old.json"), 1);
        DraftFolders.clear(root.resolve("x.partial"));
        assertTrue(Files.exists(root.resolve("x/keep.json")));
    }

    @Test
    void promoteRenamesTheDraftToTheTarget() throws IOException {
        file(root.resolve("x.partial/a/b.json"), 5);
        DraftFolders.promote(root.resolve("x.partial"), root.resolve("x"));
        assertArrayEquals(new byte[5], Files.readAllBytes(root.resolve("x/a/b.json")));
        assertFalse(Files.exists(root.resolve("x.partial")));
    }

    @Test
    void promoteNeverReplacesAnExistingTarget() throws IOException {
        file(root.resolve("x.partial/new.json"), 1);
        file(root.resolve("x/old.json"), 2);
        assertThrows(FileAlreadyExistsException.class, () -> DraftFolders.promote(root.resolve("x.partial"), root.resolve("x")));
        assertTrue(Files.exists(root.resolve("x/old.json")));
        assertFalse(Files.exists(root.resolve("x/new.json")));
        assertTrue(Files.exists(root.resolve("x.partial/new.json")));
    }

    @Test
    void deleteTreeRemovesNestedFoldersAndFiles() throws IOException {
        file(root.resolve("t/a/b/c/d.json"), 1);
        file(root.resolve("t/e.json"), 1);
        FolderDeleter.deleteTree(root.resolve("t"));
        assertFalse(Files.exists(root.resolve("t")));
    }
}
