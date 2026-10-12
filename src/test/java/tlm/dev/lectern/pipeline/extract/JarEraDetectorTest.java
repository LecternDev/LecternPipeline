package tlm.dev.lectern.pipeline.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlm.dev.lectern.pipeline.fixtures.FetchFixtures;
import tlm.dev.lectern.pipeline.fixtures.JarFixtures;

class JarEraDetectorTest {

    private final JarEraDetector detector = new JarEraDetector();

    @TempDir
    Path directory;

    private Path jar() {
        return directory.resolve("server.jar");
    }

    @Test
    void jarWhoseMainClassIsTheBundlerIsABundler() {
        assertInstanceOf(JarEra.Bundler.class, detector.detect(JarFixtures.bundlerJar(jar())));
    }

    @Test
    void jarWithTheDataGeneratorClassAndAnotherMainClassIsSimple() {
        assertInstanceOf(JarEra.Simple.class, detector.detect(JarFixtures.simpleJar(jar())));
    }

    @Test
    void simpleJarWithoutAMainClassHeaderIsStillSimple() {
        Path file = JarFixtures.jarWithManifest(jar(), "Manifest-Version: 1.0\r\n\r\n", true);
        assertInstanceOf(JarEra.Simple.class, detector.detect(file));
    }

    @Test
    void headerNameIsCaseInsensitiveAndFoldedLinesAreJoined() {
        Path folded = JarFixtures.jarWithManifest(jar(), "Manifest-Version: 1.0\nmain-class: net.minecraft.bun\n dler.Main\n\n", false);
        assertInstanceOf(JarEra.Bundler.class, detector.detect(folded));
    }

    @Test
    void bundlerMainClassIsFoundWhateverThePositionInTheMainSection() {
        Path file = JarFixtures.jarWithManifest(jar(), "Created-By: invented\nMain-Class: net.minecraft.bundler.Main\nBundler-Format: 1.0\n\n", false);
        assertInstanceOf(JarEra.Bundler.class, detector.detect(file));
    }

    @Test
    void mainClassInALaterSectionIsIgnored() {
        Path file = JarFixtures.jarWithManifest(jar(), "Manifest-Version: 1.0\n\nName: part\nMain-Class: net.minecraft.bundler.Main\n", false);
        UnsupportedJarException failure = assertThrows(UnsupportedJarException.class, () -> detector.detect(file));
        assertTrue(failure.getMessage().contains("absent"), failure.getMessage());
    }

    @Test
    void manifestLargerThanTheReadLimitStillGivesTheAnswerOfItsFirstLines() {
        String junk = "X-Padding: " + "a".repeat(3 * 1024 * 1024) + "\n";
        Path file = JarFixtures.jarWithManifest(jar(), "Main-Class: net.minecraft.bundler.Main\n" + junk + "\n", false);
        assertInstanceOf(JarEra.Bundler.class, detector.detect(file));
    }

    @Test
    void jarWithAnotherMainClassAndNoDataGeneratorClassIsUnsupported() {
        Path file = JarFixtures.jarWithManifest(jar(), "Main-Class: invented.Other\n\n", false);
        UnsupportedJarException failure = assertThrows(UnsupportedJarException.class, () -> detector.detect(file));
        assertTrue(failure.getMessage().contains("invented.Other"), failure.getMessage());
        assertTrue(failure.getMessage().contains("net/minecraft/data/Main.class"), failure.getMessage());
        assertTrue(failure.getMessage().contains(file.toString()), failure.getMessage());
    }

    @Test
    void jarWithoutAManifestIsUnsupported() {
        Path file = JarFixtures.jarWithoutManifest(jar());
        UnsupportedJarException failure = assertThrows(UnsupportedJarException.class, () -> detector.detect(file));
        assertTrue(failure.getMessage().contains("META-INF/MANIFEST.MF"), failure.getMessage());
    }

    @Test
    void fileThatIsNotAZipArchiveIsUnsupported() throws IOException {
        Path file = jar();
        Files.write(file, FetchFixtures.inventedBytes(4, 5000));
        assertThrows(UnsupportedJarException.class, () -> detector.detect(file));
    }

    @Test
    void emptyAndMissingFilesAreUnsupported() throws IOException {
        Path empty = Files.write(directory.resolve("empty.jar"), new byte[0]);
        assertThrows(UnsupportedJarException.class, () -> detector.detect(empty));
        assertThrows(UnsupportedJarException.class, () -> detector.detect(directory.resolve("absent.jar")));
    }

    @Test
    void detectionExtractsNothing() throws IOException {
        JarFixtures.bundlerJar(jar());
        detector.detect(jar());
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(List.of("server.jar"), files.map(path -> path.getFileName().toString()).toList());
        }
    }
}
