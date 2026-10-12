package tlm.dev.lectern.pipeline.fetch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlm.dev.lectern.pipeline.model.VersionId;

class JarStoreTest {

    private static final VersionId ID = new VersionId("99.1");

    @TempDir
    Path workbench;

    private Path jarsRoot;
    private JarStore store;

    @BeforeEach
    void setUp() {
        jarsRoot = workbench.resolve("jars");
        store = new JarStore(jarsRoot);
    }

    @Test
    void jarPathIsServerJarInTheFolderOfTheVersion() {
        assertEquals(jarsRoot.resolve("99.1").resolve("server.jar"), store.jarPath(ID));
    }

    @Test
    void nothingExistsBeforeThePartFileIsCreated() {
        assertFalse(store.hasJar(ID));
        assertFalse(Files.exists(jarsRoot));
    }

    @Test
    void partFileIsCreatedInTheFolderOfTheVersionWithAUniqueName() throws IOException {
        Path first = store.newPartFile(ID);
        Path second = store.newPartFile(ID);
        assertEquals(jarsRoot.resolve("99.1"), first.getParent());
        assertEquals(jarsRoot.resolve("99.1"), second.getParent());
        assertNotEquals(first, second);
        assertTrue(first.getFileName().toString().endsWith(".jar.part"));
        assertFalse(store.hasJar(ID));
    }

    @Test
    void publishMovesThePartFileToServerJar() throws IOException {
        Path part = store.newPartFile(ID);
        Files.write(part, new byte[] {1, 2, 3});
        store.publish(part, ID);
        assertTrue(store.hasJar(ID));
        assertFalse(Files.exists(part));
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(store.jarPath(ID)));
        assertEquals(3, store.sizeOf(store.jarPath(ID)));
    }

    @Test
    void publishNeverReplacesAnExistingJar() throws IOException {
        Path part = store.newPartFile(ID);
        Files.write(part, new byte[] {1});
        Files.write(store.jarPath(ID), new byte[] {9, 9});
        assertThrows(UncheckedIOException.class, () -> store.publish(part, ID));
        assertArrayEquals(new byte[] {9, 9}, Files.readAllBytes(store.jarPath(ID)));
    }

    @Test
    void discardRemovesThePartFileAndToleratesAMissingOne() {
        Path part = store.newPartFile(ID);
        store.discard(part);
        assertFalse(Files.exists(part));
        store.discard(part);
    }

    @Test
    void sizeOfAMissingFileIsAnError() {
        assertThrows(UncheckedIOException.class, () -> store.sizeOf(jarsRoot.resolve("absent")));
    }
}
