package tlm.dev.lectern.pipeline.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlm.dev.lectern.pipeline.fixtures.TreeFixtures;
import tlm.dev.lectern.pipeline.model.VersionId;

class TreeScannerTest {

    private static final VersionId ID = new VersionId("99.1");

    private final TreeScanner scanner = new TreeScanner();

    @TempDir
    Path temporary;

    private Path root() {
        return temporary.resolve("99.1");
    }

    private UnexpectedFileException refusal(DataAllowlist.Layout layout) {
        return assertThrows(UnexpectedFileException.class, () -> scanner.scan(ID, root(), layout));
    }

    @Test
    void keptFilesAreListedSortedWithForwardSlashesAndTheCacheIsSkipped() {
        TreeFixtures.demoGeneratedTree(temporary, "99.1");
        assertEquals(List.of(
                "data/demo/recipe/thing.json",
                "data/minecraft/datapacks/demo_pack/pack.mcmeta",
                "reports/gadgets.json",
                "reports/minecraft/components/item/demo_lamp.json"),
                scanner.scan(ID, root(), DataAllowlist.Layout.GENERATED));
    }

    @Test
    void emptyFoldersAreNotFilesAndAreTolerated() throws IOException {
        TreeFixtures.write(root(), "reports/a.json", "{}");
        Files.createDirectories(root().resolve("tmp"));
        Files.createDirectories(root().resolve("data/empty/inside"));
        assertEquals(List.of("reports/a.json"), scanner.scan(ID, root(), DataAllowlist.Layout.GENERATED));
    }

    @Test
    void cacheContentIsNeverExaminedWhateverItHolds() {
        TreeFixtures.write(root(), "reports/a.json", "{}");
        TreeFixtures.write(root(), ".cache/deep/er/odd.exe", new byte[] {1});
        assertEquals(List.of("reports/a.json"), scanner.scan(ID, root(), DataAllowlist.Layout.GENERATED));
    }

    @Test
    void fileWithAnotherExtensionStopsNamingTheVersionAndTheFile() {
        TreeFixtures.write(root(), "reports/a.json", "{}");
        TreeFixtures.write(root(), "data/minecraft/structure/demo.nbt", new byte[] {1});
        UnexpectedFileException failure = refusal(DataAllowlist.Layout.GENERATED);
        assertTrue(failure.getMessage().contains("Version 99.1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("data/minecraft/structure/demo.nbt"), failure.getMessage());
        assertTrue(failure.getMessage().contains("extension"), failure.getMessage());
        assertTrue(failure.getMessage().contains("decided by a person"), failure.getMessage());
    }

    @Test
    void fileOutsideReportsAndDataStopsNamingIt() {
        TreeFixtures.write(root(), "reports/a.json", "{}");
        TreeFixtures.write(root(), "version.json", "{}");
        UnexpectedFileException failure = refusal(DataAllowlist.Layout.GENERATED);
        assertTrue(failure.getMessage().contains("'version.json'"), failure.getMessage());
        assertTrue(failure.getMessage().contains("outside reports/ and data/"), failure.getMessage());
    }

    @Test
    void fileInsideAnUnknownTopLevelFolderStopsEvenIfTheFolderLooksHarmless() {
        TreeFixtures.write(root(), "tmp/leftover.json", "{}");
        assertTrue(refusal(DataAllowlist.Layout.GENERATED).getMessage().contains("tmp/leftover.json"));
    }

    @Test
    void symbolicLinkToAFileStopsNamingIt() throws IOException {
        TreeFixtures.write(root(), "reports/real.json", "{}");
        Path link = root().resolve("reports/link.json");
        assumeTrue(trySymbolicLink(link, root().resolve("reports/real.json")), "this machine cannot create symbolic links, the rule is still covered by the allowlist tests");
        UnexpectedFileException failure = refusal(DataAllowlist.Layout.GENERATED);
        assertTrue(failure.getMessage().contains("reports/link.json"), failure.getMessage());
        assertTrue(failure.getMessage().contains("symbolic link"), failure.getMessage());
    }

    @Test
    void linkToAFolderIsNotFollowedAndStops() throws IOException {
        TreeFixtures.write(temporary.resolve("elsewhere"), "x.json", "{}");
        TreeFixtures.write(root(), "reports/a.json", "{}");
        Path link = root().resolve("data");
        assumeTrue(tryFolderLink(link, temporary.resolve("elsewhere")), "this machine can create neither symbolic links nor junctions");
        UnexpectedFileException failure = refusal(DataAllowlist.Layout.GENERATED);
        assertTrue(failure.getMessage().contains("'data'"), failure.getMessage());
        assertTrue(failure.getMessage().contains("symbolic link"), failure.getMessage());
    }

    @Test
    void linkNamedLikeTheCacheStillStops() throws IOException {
        TreeFixtures.write(root(), "reports/a.json", "{}");
        Files.createDirectories(temporary.resolve("reports-target"));
        assumeTrue(tryFolderLink(root().resolve(".cache"), temporary.resolve("reports-target")), "this machine can create neither symbolic links nor junctions");
        assertTrue(refusal(DataAllowlist.Layout.GENERATED).getMessage().contains("'.cache'"));
    }

    @Test
    void rootThatIsALinkStopsNamingIt() throws IOException {
        TreeFixtures.write(temporary.resolve("real"), "reports/a.json", "{}");
        Path link = temporary.resolve("99.1");
        assumeTrue(tryFolderLink(link, temporary.resolve("real")), "this machine can create neither symbolic links nor junctions");
        UnexpectedFileException failure = refusal(DataAllowlist.Layout.GENERATED);
        assertTrue(failure.getMessage().contains("'99.1'"), failure.getMessage());
    }

    @Test
    void rootThatIsNotAFolderStops() throws IOException {
        Files.writeString(root(), "{}");
        UnexpectedFileException failure = refusal(DataAllowlist.Layout.GENERATED);
        assertTrue(failure.getMessage().contains("not a plain folder"), failure.getMessage());
    }

    @Test
    void normalizedLayoutKeepsMetaJsonAndRefusesAnythingElseAtTheRoot() {
        TreeFixtures.write(root(), "meta.json", "{}");
        TreeFixtures.write(root(), "reports/a.json", "{}");
        assertEquals(List.of("meta.json", "reports/a.json"), scanner.scan(ID, root(), DataAllowlist.Layout.NORMALIZED));
        TreeFixtures.write(root(), "stray.json", "{}");
        assertTrue(refusal(DataAllowlist.Layout.NORMALIZED).getMessage().contains("stray.json"));
    }

    @Test
    void normalizedLayoutDoesNotIgnoreACacheFolder() {
        TreeFixtures.write(root(), "meta.json", "{}");
        TreeFixtures.write(root(), ".cache/x", "x");
        assertTrue(refusal(DataAllowlist.Layout.NORMALIZED).getMessage().contains(".cache/x"));
    }

    private static boolean trySymbolicLink(Path link, Path target) throws IOException {
        try {
            Files.createDirectories(link.getParent());
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | SecurityException | IOException exception) {
            return false;
        }
    }

    private static boolean tryFolderLink(Path link, Path target) throws IOException {
        if (trySymbolicLink(link, target)) {
            return true;
        }
        if (!System.getProperty("os.name").startsWith("Windows")) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString()).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0 && Files.exists(link);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
