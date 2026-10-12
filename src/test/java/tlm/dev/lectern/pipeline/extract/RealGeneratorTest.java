package tlm.dev.lectern.pipeline.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.process.JdkProcessLauncher;

class RealGeneratorTest {

    private static final Path WORKBENCH = Path.of("").toAbsolutePath().resolveSibling("LecternWorkbench");
    private static final Path JAVA = Path.of(System.getProperty("java.home"), "bin", "java");
    private static final String CACHE_FOLDER = ".cache";
    private static final int FILES_OF_26_3_WITHOUT_CACHE = 10_038;

    @TempDir
    Path temporary;

    private static Path jarOf(String id) {
        return WORKBENCH.resolve("jars").resolve(id).resolve("server.jar");
    }

    private static Path referenceOf(String id) {
        return WORKBENCH.resolve("generated").resolve(id);
    }

    private static void requireCorpus(String id) {
        assumeTrue(Files.isRegularFile(jarOf(id)) && Files.isDirectory(referenceOf(id)),
                "Needs " + jarOf(id) + " and " + referenceOf(id) + ". From the Lectern folder run: LecternPipeline/gradlew -p LecternPipeline run --args=\"extract "
                        + id + "\" (it downloads the jar from Mojang and runs the official data generator)");
    }

    private ExtractionResult extractInto(String id, String folderName) {
        Path root = temporary.resolve(folderName);
        ExtractionSettings settings = new ExtractionSettings(root.resolve("generated"), root.resolve("scratch"), JAVA, Duration.ofMinutes(5));
        ExtractionService service = new ExtractionService(new JdkProcessLauncher(), new JarEraDetector(), settings, Clock.systemUTC());
        return service.extract(new VersionId(id), jarOf(id));
    }

    private static List<String> filesWithoutCache(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path))
                    .filter(relative -> !relative.getName(0).toString().equals(CACHE_FOLDER))
                    .map(relative -> relative.toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    private static List<String> differences(Path expected, Path actual) throws IOException {
        List<String> expectedFiles = filesWithoutCache(expected);
        List<String> actualFiles = filesWithoutCache(actual);
        List<String> found = new ArrayList<>();
        List<String> missing = new ArrayList<>(expectedFiles);
        missing.removeAll(actualFiles);
        List<String> extra = new ArrayList<>(actualFiles);
        extra.removeAll(expectedFiles);
        missing.stream().limit(5).forEach(file -> found.add("missing " + file));
        extra.stream().limit(5).forEach(file -> found.add("extra " + file));
        for (String file : expectedFiles) {
            if (found.size() >= 10) {
                break;
            }
            Path other = actual.resolve(file);
            if (Files.exists(other) && Files.mismatch(expected.resolve(file), other) != -1) {
                found.add("different " + file);
            }
        }
        return found;
    }

    @ParameterizedTest
    @CsvSource({"26.3,Bundler", "1.20.4,Bundler", "1.14.4,Simple"})
    void extractionGivesTheSameFilesAndBytesAsTheWorkbenchOutsideTheCache(String id, String era) throws IOException {
        requireCorpus(id);
        JarEra detected = new JarEraDetector().detect(jarOf(id));
        assertEquals(era, detected.getClass().getSimpleName());
        ExtractionResult result = extractInto(id, "run");
        assertEquals(List.of(), differences(referenceOf(id), result.output()));
        assertEquals(filesWithoutCache(referenceOf(id)).size(), result.summary().filesWithoutCache());
        assertTrue(result.summary().files() > result.summary().filesWithoutCache(), "the generator cache folder is kept in the raw output");
    }

    @Test
    void release263HasTheExpectedNumberOfFilesOutsideTheCache() throws IOException {
        requireCorpus("26.3");
        assertEquals(FILES_OF_26_3_WITHOUT_CACHE, filesWithoutCache(referenceOf("26.3")).size());
    }

    @Test
    void twoRunsOfTheSameJarGiveTheSameOutputOutsideTheCache() throws IOException {
        requireCorpus("26.3");
        ExtractionResult first = extractInto("26.3", "first");
        ExtractionResult second = extractInto("26.3", "second");
        assertEquals(List.of(), differences(first.output(), second.output()));
        assertEquals(first.summary().filesWithoutCache(), second.summary().filesWithoutCache());
        assertEquals(first.summary().bytesWithoutCache(), second.summary().bytesWithoutCache());
    }

    @Test
    void realBundlerJarOf263IsDetectedWithoutExtractingAnything() throws IOException {
        requireCorpus("26.3");
        assertInstanceOf(JarEra.Bundler.class, new JarEraDetector().detect(jarOf("26.3")));
        try (Stream<Path> files = Files.list(jarOf("26.3").getParent())) {
            assertEquals(List.of("server.jar"), files.map(path -> path.getFileName().toString()).toList());
        }
    }
}
