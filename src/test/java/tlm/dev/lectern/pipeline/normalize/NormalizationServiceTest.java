package tlm.dev.lectern.pipeline.normalize;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlm.dev.lectern.pipeline.fixtures.SteppingClock;
import tlm.dev.lectern.pipeline.fixtures.TreeFixtures;
import tlm.dev.lectern.pipeline.json.CanonicalJsonWriter;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.model.VersionKind;

class NormalizationServiceTest {

    private static final VersionId ID = new VersionId("99.1");
    private static final Sha1 JAR_SHA1 = new Sha1("0123456789abcdef0123456789abcdef01234567");

    @TempDir
    Path workbench;

    private Path generatedRoot;
    private Path normalizedRoot;
    private NormalizationService service;

    @BeforeEach
    void setUp() {
        generatedRoot = workbench.resolve("generated");
        normalizedRoot = workbench.resolve("normalized");
        service = serviceFor(generatedRoot, normalizedRoot);
    }

    private static NormalizationService serviceFor(Path generated, Path normalized) {
        NormalizationTools tools = new NormalizationTools(new TreeScanner(), new JsonTextReader(), new CanonicalJsonWriter());
        return new NormalizationService(tools, new VersionMetaWriter(new CanonicalJsonWriter()),
                new NormalizationSettings(generated, normalized), new SteppingClock(Instant.parse("2090-01-01T00:00:00Z"), Duration.ofMillis(3200)));
    }

    private static VersionMeta meta(VersionId id) {
        return new VersionMeta(id, new VersionKind.Snapshot(), Instant.parse("2090-01-02T03:04:05Z"),
                Optional.of(new VersionId("98.9")), Optional.empty(), JAR_SHA1, Optional.empty());
    }

    private String normalizedText(String relative) {
        return TreeFixtures.read(normalizedRoot.resolve("99.1").resolve(relative));
    }

    @Test
    void generatedTreeIsRewrittenAsCanonicalJsonAtTheSameRelativePaths() {
        TreeFixtures.demoGeneratedTree(generatedRoot, "99.1");
        NormalizationResult result = service.normalize(meta(ID));
        assertEquals(List.of("data/demo/recipe/thing.json", "data/minecraft/datapacks/demo_pack/pack.mcmeta", "reports/gadgets.json",
                "reports/minecraft/components/item/demo_lamp.json"), result.files());
        assertEquals("{\n  \"demo:chest\": {\n    \"protocol_id\": 0\n  },\n  \"demo:lamp\": {\n    \"protocol_id\": 1\n  }\n}\n", normalizedText("reports/gadgets.json"));
        assertEquals("{\n  \"components\": {\n    \"demo:a\": [],\n    \"demo:b\": 1.0\n  }\n}\n", normalizedText("reports/minecraft/components/item/demo_lamp.json"));
        assertEquals("{\n  \"a\": 0.50,\n  \"b\": 1\n}\n", normalizedText("data/demo/recipe/thing.json"));
        assertEquals("{\n  \"pack\": {\n    \"description\": \"demo\"\n  }\n}\n", normalizedText("data/minecraft/datapacks/demo_pack/pack.mcmeta"));
    }

    @Test
    void outputHoldsOnlyTheKeptFilesPlusMetaJsonAndNeverTheCache() {
        TreeFixtures.demoGeneratedTree(generatedRoot, "99.1");
        service.normalize(meta(ID));
        assertEquals(List.of("data/demo/recipe/thing.json", "data/minecraft/datapacks/demo_pack/pack.mcmeta", "meta.json",
                "reports/gadgets.json", "reports/minecraft/components/item/demo_lamp.json"), TreeFixtures.filesUnder(normalizedRoot.resolve("99.1")));
        assertFalse(Files.exists(normalizedRoot.resolve("99.1.partial")));
    }

    @Test
    void metaJsonIsWrittenFromTheGivenMetaAtTheRootOfTheVersionFolder() {
        TreeFixtures.demoGeneratedTree(generatedRoot, "99.1");
        service.normalize(meta(ID));
        assertTrue(normalizedText("meta.json").contains("\"step_base\": \"98.9\""));
        assertTrue(normalizedText("meta.json").contains("\"kind\": \"snapshot\""));
    }

    @Test
    void resultCountsFilesBytesAndFilesThatDifferFromTheGeneratorBytes() {
        Path root = generatedRoot.resolve("99.1");
        TreeFixtures.write(root, "reports/already.json", "{\n  \"a\": 1\n}\n");
        TreeFixtures.write(root, "reports/unsorted.json", "{\"b\": 1, \"a\": 2}");
        TreeFixtures.write(root, "data/x.json", "{}");
        NormalizationResult result = service.normalize(meta(ID));
        assertEquals(3, result.files().size());
        assertEquals(2, result.rewritten());
        assertEquals("{\n  \"a\": 1\n}\n".length() + "{\"b\": 1, \"a\": 2}".length() + 2, result.bytesRead());
        assertEquals("{\n  \"a\": 1\n}\n".length() + "{\n  \"a\": 2,\n  \"b\": 1\n}\n".length() + "{}\n".length(), result.bytesWritten());
        assertEquals(Duration.ofMillis(3200), result.duration());
        assertEquals(normalizedRoot.resolve("99.1"), result.output());
    }

    @Test
    void normalizingTheNormalizedOutputGivesTheSameBytes() throws IOException {
        TreeFixtures.demoGeneratedTree(generatedRoot, "99.1");
        service.normalize(meta(ID));
        Path secondGenerated = workbench.resolve("second-generated");
        for (String relative : TreeFixtures.filesUnder(normalizedRoot.resolve("99.1"))) {
            if (!relative.equals("meta.json")) {
                TreeFixtures.write(secondGenerated.resolve("99.1"), relative, Files.readAllBytes(normalizedRoot.resolve("99.1").resolve(relative)));
            }
        }
        NormalizationService second = serviceFor(secondGenerated, workbench.resolve("second-normalized"));
        NormalizationResult result = second.normalize(meta(ID));
        assertEquals(0, result.rewritten());
        for (String relative : result.files()) {
            assertArrayEquals(Files.readAllBytes(normalizedRoot.resolve("99.1").resolve(relative)),
                    Files.readAllBytes(workbench.resolve("second-normalized").resolve("99.1").resolve(relative)), relative);
        }
    }

    @Test
    void canonicalizeIsIdempotent() {
        byte[] once = service.canonicalize(ID, "reports/x.json", "{\"z\": [3, 2], \"a\": 0.50}".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(once, service.canonicalize(ID, "reports/x.json", once));
    }

    @Test
    void existingNormalizedFolderIsRefusedAndLeftUntouched() throws IOException {
        TreeFixtures.demoGeneratedTree(generatedRoot, "99.1");
        TreeFixtures.write(normalizedRoot.resolve("99.1"), "reports/old.json", "{}");
        NormalizationException failure = assertThrows(NormalizationException.class, () -> service.normalize(meta(ID)));
        assertTrue(failure.getMessage().contains("already exists"), failure.getMessage());
        assertEquals(List.of("reports/old.json"), TreeFixtures.filesUnder(normalizedRoot.resolve("99.1")));
        assertEquals("{}", normalizedText("reports/old.json"));
    }

    @Test
    void requireFreshOutputRefusesAnExistingFolderEvenIfEmpty() throws IOException {
        Files.createDirectories(normalizedRoot.resolve("99.1"));
        assertThrows(NormalizationException.class, () -> service.requireFreshOutput(ID));
    }

    @Test
    void missingGeneratedFolderAsksForExtractionFirst() {
        NormalizationException failure = assertThrows(NormalizationException.class, () -> service.normalize(meta(ID)));
        assertTrue(failure.getMessage().contains("extract the version first"), failure.getMessage());
        assertFalse(Files.exists(normalizedRoot));
    }

    @Test
    void unlistedFileStopsBeforeAnythingIsWrittenNamingVersionAndFile() {
        TreeFixtures.demoGeneratedTree(generatedRoot, "99.1");
        TreeFixtures.write(generatedRoot.resolve("99.1"), "data/minecraft/structure/demo.nbt", new byte[] {1, 2});
        UnexpectedFileException failure = assertThrows(UnexpectedFileException.class, () -> service.normalize(meta(ID)));
        assertTrue(failure.getMessage().contains("Version 99.1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("data/minecraft/structure/demo.nbt"), failure.getMessage());
        assertFalse(Files.exists(normalizedRoot), "nothing may be written, not even the draft folder");
    }

    @Test
    void fileOutsideReportsAndDataStopsBeforeAnythingIsWritten() {
        TreeFixtures.demoGeneratedTree(generatedRoot, "99.1");
        TreeFixtures.write(generatedRoot.resolve("99.1"), "version.json", "{}");
        assertThrows(UnexpectedFileException.class, () -> service.normalize(meta(ID)));
        assertFalse(Files.exists(normalizedRoot));
    }

    @Test
    void invalidJsonStopsNamingTheFileAndLeavesOnlyTheDraft() {
        TreeFixtures.demoGeneratedTree(generatedRoot, "99.1");
        TreeFixtures.write(generatedRoot.resolve("99.1"), "reports/zz-broken.json", "{\"a\": ");
        NormalizationException failure = assertThrows(NormalizationException.class, () -> service.normalize(meta(ID)));
        assertTrue(failure.getMessage().contains("Version 99.1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("reports/zz-broken.json"), failure.getMessage());
        assertFalse(Files.exists(normalizedRoot.resolve("99.1")));
        assertTrue(Files.isDirectory(normalizedRoot.resolve("99.1.partial")));
    }

    @Test
    void invalidUtf8StopsNamingTheFile() {
        TreeFixtures.demoGeneratedTree(generatedRoot, "99.1");
        TreeFixtures.write(generatedRoot.resolve("99.1"), "data/bad.json", new byte[] {'{', '"', 'a', '"', ':', '"', (byte) 0xC3, '"', '}'});
        NormalizationException failure = assertThrows(NormalizationException.class, () -> service.normalize(meta(ID)));
        assertTrue(failure.getMessage().contains("data/bad.json"), failure.getMessage());
        assertFalse(Files.exists(normalizedRoot.resolve("99.1")));
    }

    @Test
    void duplicateKeyInAFileStopsInsteadOfKeepingTheLastValue() {
        TreeFixtures.demoGeneratedTree(generatedRoot, "99.1");
        TreeFixtures.write(generatedRoot.resolve("99.1"), "data/dup.json", "{\"a\": 1, \"a\": 2}");
        NormalizationException failure = assertThrows(NormalizationException.class, () -> service.normalize(meta(ID)));
        assertTrue(failure.getMessage().contains("data/dup.json"), failure.getMessage());
    }

    @Test
    void nextNormalizationReplacesTheDraftOfAFailedOne() {
        TreeFixtures.demoGeneratedTree(generatedRoot, "99.1");
        Path broken = generatedRoot.resolve("99.1/reports/zz-broken.json");
        TreeFixtures.write(generatedRoot.resolve("99.1"), "reports/zz-broken.json", "{");
        assertThrows(NormalizationException.class, () -> service.normalize(meta(ID)));
        assertTrue(Files.exists(normalizedRoot.resolve("99.1.partial/reports/gadgets.json")));
        try {
            Files.delete(broken);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
        TreeFixtures.write(normalizedRoot.resolve("99.1.partial"), "stale.json", "{}");
        service.normalize(meta(ID));
        assertTrue(Files.isRegularFile(normalizedRoot.resolve("99.1/reports/gadgets.json")));
        assertFalse(Files.exists(normalizedRoot.resolve("99.1/stale.json")));
        assertFalse(Files.exists(normalizedRoot.resolve("99.1.partial")));
    }

    @Test
    void generatedFolderIsNeverModified() throws IOException {
        Path root = TreeFixtures.demoGeneratedTree(generatedRoot, "99.1");
        List<String> before = TreeFixtures.filesUnder(root);
        byte[] gadgets = Files.readAllBytes(root.resolve("reports/gadgets.json"));
        service.normalize(meta(ID));
        assertEquals(before, TreeFixtures.filesUnder(root));
        assertArrayEquals(gadgets, Files.readAllBytes(root.resolve("reports/gadgets.json")));
    }

    @Test
    void finishedFolderAppearingDuringTheRunIsNotReplaced() {
        TreeFixtures.demoGeneratedTree(generatedRoot, "99.1");
        Clock racingClock = new Clock() {
            private boolean first = true;

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                if (first) {
                    first = false;
                    TreeFixtures.write(normalizedRoot.resolve("99.1"), "reports/other.json", "{}");
                }
                return Instant.parse("2090-01-01T00:00:00Z");
            }
        };
        NormalizationService racing = new NormalizationService(
                new NormalizationTools(new TreeScanner(), new JsonTextReader(), new CanonicalJsonWriter()),
                new VersionMetaWriter(new CanonicalJsonWriter()),
                new NormalizationSettings(generatedRoot, normalizedRoot),
                racingClock);
        NormalizationException failure = assertThrows(NormalizationException.class, () -> racing.normalize(meta(ID)));
        assertTrue(failure.getMessage().contains("appeared during the run"), failure.getMessage());
        assertEquals(List.of("reports/other.json"), TreeFixtures.filesUnder(normalizedRoot.resolve("99.1")));
    }
}
