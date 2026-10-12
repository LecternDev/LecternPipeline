package tlm.dev.lectern.pipeline.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tlm.dev.lectern.pipeline.json.CanonicalJsonWriter;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.json.JsonValue;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.model.VersionKind;

class RealNormalizationTest {

    private static final Path WORKBENCH = Path.of("").toAbsolutePath().resolveSibling("LecternWorkbench");
    private static final String CACHE_FOLDER = ".cache";
    private static final int FILES_OF_26_3 = 10_038;
    private static final int FILES_OF_26_3_WITH_ONLY_A_FINAL_NEWLINE_ADDED = 3_574;
    private static final int FILES_OF_26_3_WITH_REORDERED_KEYS = 6_464;

    @TempDir
    Path temporary;

    private final JsonTextReader reader = new JsonTextReader();

    private static Path generatedOf(String id) {
        return WORKBENCH.resolve("generated").resolve(id);
    }

    private static Path jarOf(String id) {
        return WORKBENCH.resolve("jars").resolve(id).resolve("server.jar");
    }

    private static void requireCorpus(String id) {
        assumeTrue(Files.isDirectory(generatedOf(id)) && Files.isRegularFile(jarOf(id)),
                "Needs " + generatedOf(id) + " and " + jarOf(id) + ". From the Lectern folder run: LecternPipeline/gradlew -p LecternPipeline run --args=\"extract "
                        + id + "\" (it downloads the jar from Mojang and runs the official data generator)");
    }

    private NormalizationResult normalizeInto(String id, String folderName) {
        NormalizationTools tools = new NormalizationTools(new TreeScanner(), reader, new CanonicalJsonWriter());
        NormalizationSettings settings = new NormalizationSettings(WORKBENCH.resolve("generated"), temporary.resolve(folderName));
        NormalizationService service = new NormalizationService(tools, new VersionMetaWriter(new CanonicalJsonWriter()), settings, Clock.systemUTC());
        VersionId versionId = new VersionId(id);
        VersionMeta meta = new VersionMeta(versionId, new VersionKind.Release(), Instant.parse("2090-01-01T00:00:00Z"), Optional.empty(), Optional.empty(),
                new Sha1("0123456789abcdef0123456789abcdef01234567"), new GameInfoReader(reader).read(versionId, jarOf(id)));
        return service.normalize(meta);
    }

    private static List<String> filesOutsideTheCache(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path))
                    .filter(relative -> !relative.getName(0).toString().equals(CACHE_FOLDER))
                    .map(relative -> relative.toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    private static List<String> allFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).map(path -> root.relativize(path).toString().replace('\\', '/')).sorted().toList();
        }
    }

    private JsonValue parse(Path file) throws IOException {
        return reader.read(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    @Test
    void release263KeepsEveryValueAndChangesBytesOnlyByTheFinalNewlineAndTheKeyOrder() throws IOException {
        requireCorpus("26.3");
        NormalizationResult result = normalizeInto("26.3", "once");
        Path source = generatedOf("26.3");
        assertEquals(filesOutsideTheCache(source).stream().filter(file -> file.endsWith(".json") || file.endsWith(".mcmeta")).toList(), result.files());
        assertEquals(FILES_OF_26_3, result.files().size());
        List<String> otherValues = new ArrayList<>();
        int finalNewlineOnly = 0;
        int reordered = 0;
        int alreadyEndingWithANewline = 0;
        for (String file : result.files()) {
            byte[] original = Files.readAllBytes(source.resolve(file));
            byte[] canonical = Files.readAllBytes(result.output().resolve(file));
            if (!parse(source.resolve(file)).equals(parse(result.output().resolve(file)))) {
                otherValues.add(file);
            }
            if (original[original.length - 1] == '\n') {
                alreadyEndingWithANewline++;
            }
            byte[] originalWithNewline = Arrays.copyOf(original, original.length + 1);
            originalWithNewline[original.length] = '\n';
            if (Arrays.equals(originalWithNewline, canonical)) {
                finalNewlineOnly++;
            } else if (!Arrays.equals(original, canonical)) {
                reordered++;
            }
        }
        assertEquals(List.of(), otherValues);
        assertEquals(0, alreadyEndingWithANewline);
        assertEquals(FILES_OF_26_3_WITH_ONLY_A_FINAL_NEWLINE_ADDED, finalNewlineOnly);
        assertEquals(FILES_OF_26_3_WITH_REORDERED_KEYS, reordered);
        assertEquals(FILES_OF_26_3, result.rewritten());
        assertEquals(result.bytesRead() + FILES_OF_26_3, result.bytesWritten());
        List<String> expectedOutput = new ArrayList<>(result.files());
        expectedOutput.add("meta.json");
        expectedOutput.sort(null);
        assertEquals(expectedOutput, allFiles(result.output()));
        assertTrue(Files.readString(result.output().resolve("meta.json")).contains("\"world_version\": 5023"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"26.3", "26.3-rc-3", "26.4-snapshot-3", "1.21.11", "1.20.4", "1.14.4"})
    void normalizingTwiceGivesTheSameBytesAndKeepsEveryValueOfTheGeneratedFiles(String id) throws IOException {
        requireCorpus(id);
        NormalizationResult first = normalizeInto(id, "first");
        NormalizationResult second = normalizeInto(id, "second");
        assertEquals(first.files(), second.files());
        assertEquals(allFiles(first.output()), allFiles(second.output()));
        assertEquals(first.bytesWritten(), second.bytesWritten());
        List<String> differentBytes = new ArrayList<>();
        for (String file : allFiles(first.output())) {
            if (Files.mismatch(first.output().resolve(file), second.output().resolve(file)) != -1) {
                differentBytes.add(file);
            }
        }
        assertEquals(List.of(), differentBytes);
        List<String> otherValues = new ArrayList<>();
        for (String file : first.files()) {
            if (!parse(generatedOf(id).resolve(file)).equals(parse(first.output().resolve(file)))) {
                otherValues.add(file);
            }
        }
        assertEquals(List.of(), otherValues);
    }
}
