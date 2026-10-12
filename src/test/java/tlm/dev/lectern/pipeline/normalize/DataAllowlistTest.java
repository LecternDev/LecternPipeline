package tlm.dev.lectern.pipeline.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DataAllowlistTest {

    private static DataAllowlist.Verdict generated(String path) {
        return DataAllowlist.check(DataAllowlist.Layout.GENERATED, path);
    }

    private static DataAllowlist.Verdict normalized(String path) {
        return DataAllowlist.check(DataAllowlist.Layout.NORMALIZED, path);
    }

    private static String reason(DataAllowlist.Verdict verdict) {
        return assertInstanceOf(DataAllowlist.Verdict.Reject.class, verdict).reason();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "reports/blocks.json",
            "reports/minecraft/components/item/demo_lamp.json",
            "reports/biome_parameters/minecraft/demo.json",
            "data/minecraft/recipe/demo.json",
            "data/minecraft/datapacks/demo_pack/pack.mcmeta",
            "data/a.json",
            "reports/x.y.json"})
    void jsonAndMcmetaUnderReportsAndDataAreKept(String path) {
        assertInstanceOf(DataAllowlist.Verdict.Keep.class, generated(path));
        assertInstanceOf(DataAllowlist.Verdict.Keep.class, normalized(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {".cache/0123abcd", ".cache/nested/deeper/file.nbt", ".cache/x.json", ".cache"})
    void anythingUnderTheTopLevelCacheFolderIsIgnoredWhenReadingGeneratorOutput(String path) {
        assertInstanceOf(DataAllowlist.Verdict.Ignore.class, generated(path));
    }

    @Test
    void cacheFolderIsNotSpecialInTheNormalizedLayoutAndNotWhenNested() {
        assertTrue(reason(normalized(".cache/x.json")).contains("outside"));
        assertInstanceOf(DataAllowlist.Verdict.Keep.class, generated("data/minecraft/.cache/x.json"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "data/structure/demo.nbt",
            "reports/notes.txt",
            "reports/readme",
            "data/lang/en_us.lang",
            "data/sound.ogg",
            "reports/blocks.json.bak",
            "reports/blocks.JSON",
            "data/pack.MCMETA",
            "reports/.json",
            "data/.mcmeta",
            "data/minecraft/recipe/demo.json5",
            "data/jar.jar"})
    void anyOtherExtensionIsRejectedNamingTheExtensionRule(String path) {
        assertTrue(reason(generated(path)).contains("extension"), path);
        assertTrue(reason(normalized(path)).contains("extension"), path);
    }

    @ParameterizedTest
    @ValueSource(strings = {"version.json", "server.jar", "tmp/x.json", "Reports/blocks.json", "Data/x.json", "other/reports/x.json", "logs/latest.log", "metadata.json"})
    void aFileOutsideReportsAndDataIsRejectedWhenReadingGeneratorOutput(String path) {
        assertTrue(reason(generated(path)).contains("outside reports/ and data/"), path);
    }

    @Test
    void metaJsonAtTheRootIsKeptOnlyInTheNormalizedLayout() {
        assertInstanceOf(DataAllowlist.Verdict.Keep.class, normalized("meta.json"));
        assertTrue(reason(generated("meta.json")).contains("outside"));
        assertTrue(reason(normalized("reports/../meta.json")).contains("leaves"));
        assertTrue(reason(normalized("other/meta.json")).contains("outside"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"reports", "data"})
    void aFileNamedLikeTheFolderIsRejected(String path) {
        assertTrue(reason(generated(path)).contains("file where the folder"), path);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "/etc/passwd.json",
            "/reports/x.json",
            "reports/../../x.json",
            "reports/../x.json",
            "../reports/x.json",
            "reports/./x.json",
            "reports//x.json",
            "reports/x.json/",
            "reports\\x.json",
            "C:/reports/x.json",
            "reports/x.json:stream",
            "reports/x\u0000.json",
            "reports/x\n.json"})
    void aPathThatLeavesTheFolderOrIsNotPlainIsRejected(String path) {
        DataAllowlist.Verdict verdict = generated(path);
        assertInstanceOf(DataAllowlist.Verdict.Reject.class, verdict, path);
    }

    @Test
    void dotDotRejectionNamesThePathProblem() {
        assertEquals("its path leaves the folder or has an empty or relative segment", reason(generated("reports/../x.json")));
    }
}
