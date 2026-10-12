package tlm.dev.lectern.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlm.dev.lectern.pipeline.fixtures.DiffFixtures;
import tlm.dev.lectern.pipeline.fixtures.FakeLauncher;
import tlm.dev.lectern.pipeline.fixtures.FetchFixtures;
import tlm.dev.lectern.pipeline.fixtures.GitFixtures;
import tlm.dev.lectern.pipeline.fixtures.JarFixtures;
import tlm.dev.lectern.pipeline.fixtures.LocalFileServer;
import tlm.dev.lectern.pipeline.fixtures.ManifestFixtures;
import tlm.dev.lectern.pipeline.fixtures.NormalizedFixtures;
import tlm.dev.lectern.pipeline.fixtures.SteppingClock;
import tlm.dev.lectern.pipeline.fixtures.TreeFixtures;
import tlm.dev.lectern.pipeline.model.VersionKind;
import tlm.dev.lectern.pipeline.process.JdkProcessLauncher;
import tlm.dev.lectern.pipeline.process.ProcessOutcome;

class PipelineMainTest {

    @TempDir
    Path base;

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private LocalFileServer server;
    private Path workbench;
    private Environment environment;
    private byte[] bundlerJar;
    private Path diffScript;

    @BeforeEach
    void setUp() throws IOException {
        server = new LocalFileServer();
        workbench = base.resolve("LecternWorkbench");
        Files.createDirectories(workbench);
        environment = new Environment(base.resolve("LecternPipeline"), base.resolve("jdk"), base.resolve("tmp"), false, Optional.empty());
        diffScript = base.resolve("tools").resolve("LecternDiff");
        Files.createDirectories(diffScript.getParent());
        Files.writeString(diffScript, "placeholder for the diff script, the launcher is a fake");
        bundlerJar = Files.readAllBytes(JarFixtures.bundlerJar(base.resolve("model-bundler.jar")));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private PipelineMain main(FakeLauncher launcher) {
        Clock clock = new SteppingClock(Instant.parse("2090-01-01T00:00:00Z"), Duration.ofMillis(6100));
        return new PipelineMain(
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8),
                environment, launcher, clock);
    }

    private int run(FakeLauncher launcher, String... args) {
        return main(launcher).run(args);
    }

    private int run(String... args) {
        return run(FakeLauncher.writingFiles("reports/blocks.json"), args);
    }

    private String out() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    private String releaseEntry(String id, byte[] jarOnServer, String sha1Declared, int hoursAfterOrigin) {
        byte[] versionJson = FetchFixtures.versionJson(id, sha1Declared, bundlerJar.length, server.uri("/jar/" + id).toString());
        server.serve("/jar/" + id, 200, jarOnServer);
        server.serve("/version/" + id, 200, versionJson);
        return ManifestFixtures.entryJson(id, "release", ManifestFixtures.hoursAfterOrigin(hoursAfterOrigin),
                server.uri("/version/" + id).toString(), FetchFixtures.sha1Hex(versionJson));
    }

    private void publishManifest(String... entries) {
        server.serve("/manifest", 200, ManifestFixtures.manifestJson(entries).getBytes(StandardCharsets.UTF_8));
    }

    private String[] arguments(String... leading) {
        return Stream.concat(Stream.of(leading), Stream.of("--workbench=" + workbench, "--manifest-url=" + server.uri("/manifest"))).toArray(String[]::new);
    }

    private List<String> filesIn(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            return walk.filter(Files::isRegularFile).map(path -> folder.relativize(path).toString().replace('\\', '/')).sorted().toList();
        }
    }

    @Test
    void listPrintsTheRecentVersionsWithKindReleaseTimeAndDiffBase() {
        List<String> entries = List.of(
                ManifestFixtures.entryJson("99.2-snapshot-1", "snapshot", ManifestFixtures.hoursAfterOrigin(300)),
                ManifestFixtures.entryJson("26w14a", "snapshot", ManifestFixtures.hoursAfterOrigin(250)),
                ManifestFixtures.entryJson("99.1", "release", ManifestFixtures.hoursAfterOrigin(200)),
                ManifestFixtures.entryJson("99.1-rc-1", "snapshot", ManifestFixtures.hoursAfterOrigin(100)),
                ManifestFixtures.entryJson("1.14 Pre-Release 1", "snapshot", ManifestFixtures.hoursAfterOrigin(50)),
                ManifestFixtures.entryJson("98.9", "release", ManifestFixtures.hoursAfterOrigin(10)));
        publishManifest(entries.toArray(new String[0]));
        int exit = run("list", "--manifest-url=" + server.uri("/manifest"));
        assertEquals("", err());
        assertEquals(0, exit);
        assertEquals("""
                ID               KIND               RELEASED              BASE       RELEASE BASE
                99.2-snapshot-1  snapshot           2090-01-13T12:00:00Z  99.1       -
                99.1             release            2090-01-09T08:00:00Z  99.1-rc-1  98.9
                99.1-rc-1        release-candidate  2090-01-05T04:00:00Z  98.9       -
                98.9             release            2090-01-01T10:00:00Z  -          -
                """, out());
    }

    @Test
    void noArgumentPrintsTheUsageAndExitsWithTwo() {
        int exit = run();
        assertEquals(2, exit);
        assertEquals("", out());
        assertEquals(PipelineConfigReader.USAGE + System.lineSeparator(), err());
    }

    @Test
    void unknownCommandExitsWithTwo() {
        assertEquals(2, run("download"));
        assertTrue(err().contains("Unknown command 'download'"), err());
    }

    @Test
    void listFetchFailureExitsWithOneAndNamesTheStatus() {
        server.serve("/manifest", 503, new byte[0]);
        assertEquals(1, run("list", "--manifest-url=" + server.uri("/manifest")));
        assertEquals("", out());
        assertTrue(err().startsWith("lectern-pipeline: "), err());
        assertTrue(err().contains("HTTP 503"), err());
    }

    @Test
    void malformedManifestExitsWithOneAndPrintsNothingOnStandardOutput() {
        server.serve("/manifest", 200, "{\"versions\": [{\"id\": \"99.1\"}]}".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, run("list", "--manifest-url=" + server.uri("/manifest")));
        assertEquals("", out());
        assertTrue(err().contains("entry 0"), err());
    }

    @Test
    void fetchDownloadsTheVerifiedJarIntoTheWorkbench() throws IOException {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        int exit = run(arguments("fetch", "99.1"));
        assertEquals("", err());
        assertEquals(0, exit);
        Path jar = workbench.resolve("jars").resolve("99.1").resolve("server.jar");
        assertArrayEquals(bundlerJar, Files.readAllBytes(jar));
        assertTrue(out().startsWith("fetched 99.1 (downloaded)\n"), out());
        assertTrue(out().contains("sha1   " + FetchFixtures.sha1Hex(bundlerJar)), out());
        assertEquals(List.of("99.1/server.jar"), filesIn(workbench.resolve("jars")));
    }

    @Test
    void fetchOfAJarAlreadyThereDoesNotDownloadItAgain() {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        assertEquals(0, run(arguments("fetch", "99.1")));
        outBytes.reset();
        assertEquals(0, run(arguments("fetch", "99.1")));
        assertTrue(out().startsWith("fetched 99.1 (already present)\n"), out());
        assertEquals(1, server.hits("/jar/99.1"));
    }

    @Test
    void fetchWithAWrongJarHashExitsWithOneNamingTheVersionAndBothHashes() throws IOException {
        byte[] served = FetchFixtures.inventedBytes(11, bundlerJar.length);
        publishManifest(releaseEntry("99.1", served, FetchFixtures.sha1Hex(bundlerJar), 10));
        assertEquals(1, run(arguments("fetch", "99.1")));
        assertEquals("", out());
        assertTrue(err().contains("Version 99.1"), err());
        assertTrue(err().contains("Expected " + FetchFixtures.sha1Hex(bundlerJar)), err());
        assertTrue(err().contains("received " + FetchFixtures.sha1Hex(served)), err());
        assertEquals(List.of(), filesIn(workbench.resolve("jars")));
    }

    @Test
    void fetchRefusesAnExcludedVersionAndSaysSo() throws IOException {
        publishManifest(ManifestFixtures.entryJson("26w14a", "snapshot", ManifestFixtures.hoursAfterOrigin(5)));
        assertEquals(1, run(arguments("fetch", "26w14a")));
        assertTrue(err().contains("exclusion list"), err());
        assertEquals(List.of(), filesIn(workbench.resolve("jars")));
    }

    @Test
    void fetchRefusesAVersionWithoutANormalShapeAndSaysSo() throws IOException {
        publishManifest(ManifestFixtures.entryJson("1.14 Pre-Release 1", "snapshot", ManifestFixtures.hoursAfterOrigin(5)));
        assertEquals(1, run(arguments("fetch", "1.14 Pre-Release 1")));
        assertTrue(err().contains("not shaped like"), err());
        assertEquals(List.of(), filesIn(workbench.resolve("jars")));
    }

    @Test
    void fetchRefusesAVersionAbsentFromTheManifestAndSaysSo() {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        assertEquals(1, run(arguments("fetch", "98.9")));
        assertTrue(err().contains("not in the Mojang manifest"), err());
        assertEquals(0, server.hits("/jar/99.1"));
    }

    @Test
    void fetchWithoutAWorkbenchFolderExitsWithOneAndSaysHowToFixIt() {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        int exit = run("fetch", "99.1", "--workbench=" + base.resolve("absent"), "--manifest-url=" + server.uri("/manifest"));
        assertEquals(1, exit);
        assertTrue(err().contains("does not exist"), err());
        assertTrue(err().contains("--workbench"), err());
        assertEquals(0, server.hits("/manifest"));
        assertFalse(Files.exists(base.resolve("absent")));
    }

    @Test
    void defaultWorkbenchIsTheFolderNextToTheCurrentDirectory() throws IOException {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        assertEquals(0, run("fetch", "99.1", "--manifest-url=" + server.uri("/manifest")));
        assertTrue(Files.isRegularFile(base.resolve("LecternWorkbench").resolve("jars").resolve("99.1").resolve("server.jar")));
    }

    @Test
    void extractFetchesTheJarThenRunsTheGeneratorAndReportsTheResult() throws IOException {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        FakeLauncher launcher = FakeLauncher.writingFiles("reports/blocks.json", "data/minecraft/recipe/a.json", ".cache/abc");
        int exit = run(launcher, arguments("extract", "99.1"));
        assertEquals("", err());
        assertEquals(0, exit);
        assertTrue(out().contains("fetched 99.1 (downloaded)"), out());
        assertTrue(out().contains("extracted 99.1"), out());
        assertTrue(out().contains("files     3 (2 without .cache)"), out());
        assertTrue(out().contains("duration  6.1 s"), out());
        assertEquals(1, launcher.calls().size());
        assertTrue(launcher.calls().get(0).command().contains("-DbundlerMainClass=net.minecraft.data.Main"));
        assertEquals(List.of(".cache/abc", "data/minecraft/recipe/a.json", "reports/blocks.json"), filesIn(workbench.resolve("generated").resolve("99.1")));
    }

    @Test
    void extractUsesTheJarAlreadyInTheWorkbenchWithoutDownloadingIt() {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        assertEquals(0, run(arguments("fetch", "99.1")));
        outBytes.reset();
        assertEquals(0, run(arguments("extract", "99.1")));
        assertTrue(out().contains("fetched 99.1 (already present)"), out());
        assertEquals(1, server.hits("/jar/99.1"));
    }

    @Test
    void extractRefusesAnExistingOutputFolderBeforeAnyNetworkAccess() throws IOException {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        Path existing = workbench.resolve("generated").resolve("99.1").resolve("reports").resolve("blocks.json");
        FakeLauncher.write(existing, 9);
        FakeLauncher launcher = FakeLauncher.writingFiles("reports/other.json");
        assertEquals(1, run(launcher, arguments("extract", "99.1")));
        assertTrue(err().contains("already exists"), err());
        assertEquals(0, server.hits("/manifest"));
        assertEquals(0, server.hits("/jar/99.1"));
        assertEquals(List.of(), launcher.calls());
        assertEquals(List.of("reports/blocks.json"), filesIn(workbench.resolve("generated").resolve("99.1")));
        assertArrayEquals(new byte[9], Files.readAllBytes(existing));
    }

    @Test
    void extractRefusesAnExcludedVersionBeforeDownloadingAnything() throws IOException {
        publishManifest(ManifestFixtures.entryJson("26w14a", "snapshot", ManifestFixtures.hoursAfterOrigin(5)));
        FakeLauncher launcher = FakeLauncher.writingFiles("reports/blocks.json");
        assertEquals(1, run(launcher, arguments("extract", "26w14a")));
        assertTrue(err().contains("exclusion list"), err());
        assertEquals(List.of(), launcher.calls());
        assertEquals(List.of(), filesIn(workbench));
    }

    @Test
    void generatorFailureExitsWithOneWithTheEndOfStderr() {
        publishManifest(releaseEntry("99.1", bundlerJar, FetchFixtures.sha1Hex(bundlerJar), 10));
        FakeLauncher launcher = new FakeLauncher(call -> new ProcessOutcome.Exited(1, "", "Exception in thread main: invented boom"));
        assertEquals(1, run(launcher, arguments("extract", "99.1")));
        assertTrue(err().startsWith("lectern-pipeline: Version 99.1"), err());
        assertTrue(err().contains("invented boom"), err());
        assertTrue(out().contains("fetched 99.1 (downloaded)"), out());
        assertFalse(out().contains("extracted"), out());
    }

    private String jarEntry(String id, String type, int hoursAfterOrigin) {
        byte[] versionJson = FetchFixtures.versionJson(id, FetchFixtures.sha1Hex(bundlerJar), bundlerJar.length, server.uri("/jar/" + id).toString());
        server.serve("/jar/" + id, 200, bundlerJar);
        server.serve("/version/" + id, 200, versionJson);
        return ManifestFixtures.entryJson(id, type, ManifestFixtures.hoursAfterOrigin(hoursAfterOrigin),
                server.uri("/version/" + id).toString(), FetchFixtures.sha1Hex(versionJson));
    }

    private void publishDemoReleaseWithItsCandidateAndPreviousRelease() {
        publishManifest(jarEntry("99.1", "release", 100), jarEntry("99.1-rc-1", "snapshot", 50), jarEntry("98.9", "release", 10));
    }

    private FakeLauncher routing(Function<FakeLauncher.Call, ProcessOutcome> diff) {
        JdkProcessLauncher real = new JdkProcessLauncher();
        return new FakeLauncher(call -> {
            String program = call.command().get(0);
            if (program.equals("git")) {
                return real.launch(call.command(), call.workingDirectory(), call.timeout());
            }
            if (program.equals(diffScript.toString())) {
                return diff.apply(call);
            }
            return generatorWritingTheDemoTree(call);
        });
    }

    private static ProcessOutcome generatorWritingTheDemoTree(FakeLauncher.Call call) {
        Path output = FakeLauncher.outputOf(call.command());
        TreeFixtures.write(output, "reports/gadgets.json", "{\"demo:lamp\": {\"protocol_id\": 1}, \"demo:chest\": {\"protocol_id\": 0}}");
        TreeFixtures.write(output, "data/demo/recipe/thing.json", "{\"b\": 1, \"a\": 0.50}");
        TreeFixtures.write(output, ".cache/abc", "cache");
        return new ProcessOutcome.Exited(0, "", "");
    }

    private static ProcessOutcome echoingDiff(FakeLauncher.Call call) {
        List<String> command = call.command();
        return new ProcessOutcome.Exited(0, DiffFixtures.canonicalDiff(
                optionValue(command, "--from"), optionValue(command, "--from-kind"), optionValue(command, "--to"), optionValue(command, "--to-kind"), 2), "");
    }

    private static String optionValue(List<String> command, String option) {
        return command.get(command.indexOf(option) + 1);
    }

    private String[] publishArguments(String id, Path repository) {
        return new String[] {"publish", id, "--workbench=" + workbench, "--manifest-url=" + server.uri("/manifest"), "--data=" + repository,
                "--diff-command=" + diffScript};
    }

    private Path dataRepository() {
        assumeTrue(GitFixtures.gitAvailable(), "git is not available on this machine");
        return GitFixtures.initRepository(base.resolve("data-repository"));
    }

    private void prepareNormalizedDemoLine() {
        Path root = workbench.resolve("normalized");
        NormalizedFixtures.write(root, "98.9", new VersionKind.Release(), Optional.empty(), Optional.empty(), Map.of("reports/gadgets.json", "{}\n"));
        NormalizedFixtures.write(root, "99.1-rc-1", new VersionKind.ReleaseCandidate(), Optional.of("98.9"), Optional.empty(),
                Map.of("reports/gadgets.json", "{\n  \"demo:chest\": 0\n}\n"));
        NormalizedFixtures.write(root, "99.1", new VersionKind.Release(), Optional.of("99.1-rc-1"), Optional.of("98.9"),
                Map.of("reports/gadgets.json", "{\n  \"demo:chest\": 0,\n  \"demo:lamp\": 1\n}\n", "data/demo/recipe/thing.json", "{\n  \"a\": 0.50,\n  \"b\": 1\n}\n"));
    }

    private void assertRepositoryUntouched(Path repository, String headBefore) {
        assertEquals(headBefore, GitFixtures.head(repository));
        assertEquals("", GitFixtures.status(repository));
        assertEquals("", GitFixtures.run(repository, "tag", "--list").strip());
        assertFalse(Files.exists(repository.resolve("reports")));
        assertFalse(Files.exists(repository.resolve("versions")));
        assertFalse(Files.exists(repository.resolve("diffs")));
    }

    @Test
    void normalizeWritesTheCanonicalTreeAndMetaJsonIntoTheWorkbench() throws IOException {
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        TreeFixtures.demoGeneratedTree(workbench.resolve("generated"), "99.1");
        int exit = run(arguments("normalize", "99.1"));
        assertEquals("", err());
        assertEquals(0, exit);
        assertTrue(out().contains("fetched 99.1 (downloaded)"), out());
        assertTrue(out().contains("normalized 99.1"), out());
        Path normalized = workbench.resolve("normalized").resolve("99.1");
        assertEquals("{\n  \"demo:chest\": {\n    \"protocol_id\": 0\n  },\n  \"demo:lamp\": {\n    \"protocol_id\": 1\n  }\n}\n",
                TreeFixtures.read(normalized.resolve("reports/gadgets.json")));
        assertEquals(List.of("data/demo/recipe/thing.json", "data/minecraft/datapacks/demo_pack/pack.mcmeta", "meta.json", "reports/gadgets.json",
                "reports/minecraft/components/item/demo_lamp.json"), TreeFixtures.filesUnder(normalized));
        assertEquals("""
                {
                  "game": {
                    "id": "99.1"
                  },
                  "id": "99.1",
                  "jar_sha1": "%s",
                  "kind": "release",
                  "release_base": "98.9",
                  "release_time": "2090-01-05T04:00:00Z",
                  "schema_version": 1,
                  "step_base": "99.1-rc-1"
                }
                """.formatted(FetchFixtures.sha1Hex(bundlerJar)), TreeFixtures.read(normalized.resolve("meta.json")));
    }

    @Test
    void normalizeRefusesAnExistingNormalizedFolderBeforeAnyNetworkAccess() {
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        TreeFixtures.demoGeneratedTree(workbench.resolve("generated"), "99.1");
        TreeFixtures.write(workbench.resolve("normalized").resolve("99.1"), "reports/old.json", "{}");
        assertEquals(1, run(arguments("normalize", "99.1")));
        assertTrue(err().contains("already exists"), err());
        assertEquals(0, server.hits("/manifest"));
        assertEquals(List.of("reports/old.json"), TreeFixtures.filesUnder(workbench.resolve("normalized").resolve("99.1")));
    }

    @Test
    void normalizeWithoutAGeneratedFolderAsksForExtraction() {
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        assertEquals(1, run(arguments("normalize", "99.1")));
        assertTrue(err().contains("extract the version first"), err());
        assertFalse(Files.exists(workbench.resolve("normalized").resolve("99.1")));
    }

    @Test
    void normalizeStopsOnAFileOutsideTheAllowlistAndWritesNothing() {
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        TreeFixtures.demoGeneratedTree(workbench.resolve("generated"), "99.1");
        TreeFixtures.write(workbench.resolve("generated").resolve("99.1"), "data/minecraft/structure/demo.nbt", new byte[] {1});
        assertEquals(1, run(arguments("normalize", "99.1")));
        assertTrue(err().contains("data/minecraft/structure/demo.nbt"), err());
        assertTrue(err().contains("Version 99.1"), err());
        assertFalse(Files.exists(workbench.resolve("normalized")));
    }

    @Test
    void normalizeRefusesAnExcludedVersion() {
        publishManifest(ManifestFixtures.entryJson("26w14a", "snapshot", ManifestFixtures.hoursAfterOrigin(5)));
        TreeFixtures.demoGeneratedTree(workbench.resolve("generated"), "26w14a");
        assertEquals(1, run(arguments("normalize", "26w14a")));
        assertTrue(err().contains("exclusion list"), err());
    }

    @Test
    void publishRunsTheWholeChainForTheVersionAndItsBasesThenCommitsRawFilesSheetDiffsAndTag() {
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        Path repository = dataRepository();
        FakeLauncher launcher = routing(PipelineMainTest::echoingDiff);
        int exit = run(launcher, publishArguments("99.1", repository));
        assertEquals("", err());
        assertEquals(0, exit);
        assertTrue(out().contains("fetched 99.1 (downloaded)"), out());
        assertTrue(out().contains("normalized 99.1\n"), out());
        assertTrue(out().contains("normalized 99.1-rc-1\n"), out());
        assertTrue(out().contains("normalized 98.9\n"), out());
        assertTrue(out().contains("diff        step.json 99.1-rc-1 -> 99.1 ("), out());
        assertTrue(out().contains("diff        release.json 98.9 -> 99.1 ("), out());
        assertTrue(out().contains("published 99.1\n"), out());
        assertTrue(out().contains("  tag         99.1\n"), out());
        assertEquals("data: 99.1", GitFixtures.run(repository, "log", "-1", "--pretty=%B").strip());
        assertEquals(List.of("data/demo/recipe/thing.json", "diffs/99.1/release.json", "diffs/99.1/step.json", "reports/gadgets.json", "versions/99.1/meta.json"),
                GitFixtures.run(repository, "diff-tree", "--root", "-r", "--no-commit-id", "--name-only", "HEAD").lines().sorted().toList());
        assertEquals(GitFixtures.head(repository), GitFixtures.run(repository, "rev-parse", "99.1^{commit}").strip());
        assertEquals(TreeFixtures.read(workbench.resolve("normalized/99.1/reports/gadgets.json")), TreeFixtures.read(repository.resolve("reports/gadgets.json")));
        assertEquals(TreeFixtures.read(workbench.resolve("normalized/99.1/meta.json")), TreeFixtures.read(repository.resolve("versions/99.1/meta.json")));
        assertEquals(DiffFixtures.canonicalDiff("99.1-rc-1", "release-candidate", "99.1", "release", 2), TreeFixtures.read(repository.resolve("diffs/99.1/step.json")));
        assertEquals(DiffFixtures.canonicalDiff("98.9", "release", "99.1", "release", 2), TreeFixtures.read(repository.resolve("diffs/99.1/release.json")));
        assertTrue(Files.isRegularFile(workbench.resolve("normalized/99.1-rc-1/meta.json")));
        assertTrue(Files.isRegularFile(workbench.resolve("normalized/98.9/meta.json")));
    }

    @Test
    void diffCommandIsCalledOncePerBaseWithTheNormalizedFoldersTheIdsAndTheKinds() {
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        Path repository = dataRepository();
        FakeLauncher launcher = routing(PipelineMainTest::echoingDiff);
        assertEquals(0, run(launcher, publishArguments("99.1", repository)));
        List<List<String>> diffCalls = launcher.calls().stream().map(FakeLauncher.Call::command).filter(command -> command.get(0).equals(diffScript.toString())).toList();
        assertEquals(List.of(
                List.of(diffScript.toString(), "diff", workbench.resolve("normalized/99.1-rc-1").toString(), workbench.resolve("normalized/99.1").toString(),
                        "--from", "99.1-rc-1", "--from-kind", "release-candidate", "--to", "99.1", "--to-kind", "release"),
                List.of(diffScript.toString(), "diff", workbench.resolve("normalized/98.9").toString(), workbench.resolve("normalized/99.1").toString(),
                        "--from", "98.9", "--from-kind", "release", "--to", "99.1", "--to-kind", "release")), diffCalls);
    }

    @Test
    void publishUsesTheNormalizedFoldersThatAreAlreadyThereWithoutTouchingTheNetwork() {
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        prepareNormalizedDemoLine();
        Path repository = dataRepository();
        assertEquals(0, run(routing(PipelineMainTest::echoingDiff), publishArguments("99.1", repository)));
        assertEquals("", err());
        assertEquals(0, server.hits("/manifest"));
        assertEquals(0, server.hits("/jar/99.1"));
        assertEquals(0, server.hits("/jar/99.1-rc-1"));
        assertEquals(0, server.hits("/jar/98.9"));
        assertFalse(out().contains("normalized 99.1"), out());
        assertEquals(List.of("diffs/99.1/release.json", "diffs/99.1/step.json", "data/demo/recipe/thing.json", "reports/gadgets.json", "versions/99.1/meta.json").stream().sorted().toList(),
                GitFixtures.run(repository, "diff-tree", "--root", "-r", "--no-commit-id", "--name-only", "HEAD").lines().sorted().toList());
    }

    @Test
    void secondVersionOfTheLineCommitsOnlyWhatChangedInTheRawData() {
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        prepareNormalizedDemoLine();
        Path repository = dataRepository();
        FakeLauncher launcher = routing(PipelineMainTest::echoingDiff);
        assertEquals(0, run(launcher, publishArguments("99.1-rc-1", repository)));
        outBytes.reset();
        assertEquals(0, run(launcher, publishArguments("99.1", repository)));
        assertEquals("", err());
        assertEquals(List.of("M\treports/gadgets.json", "A\tdata/demo/recipe/thing.json", "A\tversions/99.1/meta.json", "A\tdiffs/99.1/step.json", "A\tdiffs/99.1/release.json").stream().sorted().toList(),
                GitFixtures.run(repository, "diff-tree", "-r", "--no-commit-id", "--name-status", "HEAD").lines().sorted().toList());
        assertEquals("99.1\n99.1-rc-1", String.join("\n", GitFixtures.run(repository, "tag", "--list").lines().sorted().toList()));
    }

    @Test
    void publishReadsTheDiffCommandFromTheEnvironmentWhenTheOptionIsAbsent() {
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        prepareNormalizedDemoLine();
        Path repository = dataRepository();
        environment = new Environment(environment.currentDirectory(), environment.javaHome(), environment.tempDirectory(), false, Optional.of(diffScript.toString()));
        int exit = run(routing(PipelineMainTest::echoingDiff), "publish", "99.1", "--workbench=" + workbench, "--manifest-url=" + server.uri("/manifest"), "--data=" + repository);
        assertEquals("", err());
        assertEquals(0, exit);
        assertTrue(Files.isRegularFile(repository.resolve("diffs/99.1/step.json")));
    }

    @Test
    void publishWithoutTheDiffCommandRefusesBeforeDownloadingAnyJar() {
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        Path repository = dataRepository();
        String head = GitFixtures.head(repository);
        int exit = run(routing(PipelineMainTest::echoingDiff), "publish", "99.1", "--workbench=" + workbench, "--manifest-url=" + server.uri("/manifest"), "--data=" + repository);
        assertEquals(2, exit);
        assertTrue(err().contains("Publishing 99.1 needs the diff command"), err());
        assertTrue(err().contains("--diff-command"), err());
        assertTrue(err().contains("LECTERN_DIFF_COMMAND"), err());
        assertEquals(0, server.hits("/jar/99.1"));
        assertEquals(0, server.hits("/jar/99.1-rc-1"));
        assertRepositoryUntouched(repository, head);
        assertFalse(Files.exists(workbench.resolve("normalized")));
    }

    @Test
    void publishOfAVersionWithoutBasesNeedsNoDiffCommand() {
        Path root = workbench.resolve("normalized");
        NormalizedFixtures.write(root, "1.21.11", new VersionKind.Release(), Optional.empty(), Optional.empty(), Map.of("reports/gadgets.json", "{}\n"));
        Path repository = dataRepository();
        publishManifest(ManifestFixtures.entryJson("1.21.11", "release", ManifestFixtures.hoursAfterOrigin(10)));
        int exit = run(routing(call -> {
            throw new AssertionError("the diff command must not run");
        }), "publish", "1.21.11", "--workbench=" + workbench, "--manifest-url=" + server.uri("/manifest"), "--data=" + repository);
        assertEquals("", err());
        assertEquals(0, exit);
        assertTrue(out().contains("  diffs       none\n"), out());
        assertFalse(Files.exists(repository.resolve("diffs")));
        assertEquals("1.21.11", GitFixtures.run(repository, "tag", "--list").strip());
    }

    @Test
    void publishStopsWhenTheDiffCommandFailsAndLeavesTheRepositoryAsItWas() {
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        prepareNormalizedDemoLine();
        Path repository = dataRepository();
        String head = GitFixtures.head(repository);
        int exit = run(routing(call -> new ProcessOutcome.Exited(1, "", "error: Version 99.1, reports/blocks.json: invented failure")), publishArguments("99.1", repository));
        assertEquals(1, exit);
        assertTrue(err().startsWith("lectern-pipeline: Version 99.1"), err());
        assertTrue(err().contains("exited with code 1"), err());
        assertTrue(err().contains("invented failure"), err());
        assertFalse(out().contains("published"), out());
        assertRepositoryUntouched(repository, head);
    }

    @Test
    void publishStopsWhenTheDiffOutputIsNotInCanonicalForm() {
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        prepareNormalizedDemoLine();
        Path repository = dataRepository();
        String head = GitFixtures.head(repository);
        int exit = run(routing(call -> new ProcessOutcome.Exited(0, echoingDiffText(call).replace("\n", "\r\n"), "")), publishArguments("99.1", repository));
        assertEquals(1, exit);
        assertTrue(err().contains("Version 99.1"), err());
        assertTrue(err().contains("not in canonical JSON form"), err());
        assertRepositoryUntouched(repository, head);
    }

    @Test
    void publishStopsWhenTheDiffNamesAnotherVersion() {
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        prepareNormalizedDemoLine();
        Path repository = dataRepository();
        String head = GitFixtures.head(repository);
        int exit = run(routing(call -> new ProcessOutcome.Exited(0, DiffFixtures.canonicalDiff("98.8", "release", "99.1", "release", 1), "")), publishArguments("99.1", repository));
        assertEquals(1, exit);
        assertTrue(err().contains("'98.8 release' as its 'from' version"), err());
        assertRepositoryUntouched(repository, head);
    }

    @Test
    void publishWithADiffScriptThatDoesNotExistSaysHowToBuildItBeforeDownloadingAnyJar() {
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        Path repository = dataRepository();
        String head = GitFixtures.head(repository);
        int exit = run(routing(PipelineMainTest::echoingDiff), "publish", "99.1", "--workbench=" + workbench, "--manifest-url=" + server.uri("/manifest"),
                "--data=" + repository, "--diff-command=" + base.resolve("tools/absent"));
        assertEquals(1, exit);
        assertTrue(err().contains("does not exist"), err());
        assertTrue(err().contains("installDist"), err());
        assertEquals(0, server.hits("/jar/99.1"));
        assertRepositoryUntouched(repository, head);
    }

    @Test
    void publishWhoseBaseCannotBeDownloadedLeavesTheRepositoryUntouched() {
        publishManifest(jarEntry("99.1", "release", 100), releaseEntry("99.1-rc-1", FetchFixtures.inventedBytes(7, bundlerJar.length), FetchFixtures.sha1Hex(bundlerJar), 50),
                jarEntry("98.9", "release", 10));
        Path repository = dataRepository();
        String head = GitFixtures.head(repository);
        int exit = run(routing(PipelineMainTest::echoingDiff), publishArguments("99.1", repository));
        assertEquals(1, exit);
        assertTrue(err().contains("Version 99.1-rc-1"), err());
        assertRepositoryUntouched(repository, head);
    }

    @Test
    void publishRefusesARepositoryThatIsNotCleanBeforeAnyNetworkAccess() {
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        Path repository = dataRepository();
        TreeFixtures.write(repository, "notes.txt", "x");
        assertEquals(1, run(routing(PipelineMainTest::echoingDiff), publishArguments("99.1", repository)));
        assertTrue(err().contains("not clean"), err());
        assertTrue(err().contains("notes.txt"), err());
        assertEquals(0, server.hits("/manifest"));
        assertFalse(Files.exists(workbench.resolve("normalized")));
    }

    @Test
    void publishRefusesARepositoryWithoutTheAttributesBeforeNormalizingAnything() {
        assumeTrue(GitFixtures.gitAvailable(), "git is not available on this machine");
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        Path repository = GitFixtures.initRepository(base.resolve("data-repository"), false);
        GitFixtures.commitFile(repository, "readme.txt", "x", "chore: first");
        assertEquals(1, run(routing(PipelineMainTest::echoingDiff), publishArguments("99.1", repository)));
        assertTrue(err().contains("reports/** -text"), err());
        assertTrue(err().contains("diffs/** -text"), err());
        assertEquals(0, server.hits("/manifest"));
        assertFalse(Files.exists(workbench.resolve("normalized")));
        assertFalse(Files.exists(repository.resolve("reports")));
    }

    @Test
    void publishToAFolderThatIsNotARepositoryExitsWithOne() throws IOException {
        assumeTrue(GitFixtures.gitAvailable(), "git is not available on this machine");
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        Path plain = Files.createDirectories(base.resolve("plain"));
        assertEquals(1, run(routing(PipelineMainTest::echoingDiff), publishArguments("99.1", plain)));
        assertTrue(err().contains("not a git repository"), err());
    }

    @Test
    void publishingTheSameVersionAgainIsRefusedBeforeAnyNetworkAccess() {
        publishDemoReleaseWithItsCandidateAndPreviousRelease();
        prepareNormalizedDemoLine();
        Path repository = dataRepository();
        FakeLauncher launcher = routing(PipelineMainTest::echoingDiff);
        assertEquals(0, run(launcher, publishArguments("99.1", repository)));
        String head = GitFixtures.head(repository);
        outBytes.reset();
        errBytes.reset();
        assertEquals(1, run(launcher, publishArguments("99.1", repository)));
        assertTrue(err().contains("published already"), err());
        assertEquals(0, server.hits("/manifest"));
        assertEquals(head, GitFixtures.head(repository));
        assertEquals("", GitFixtures.status(repository));
    }

    private String echoingDiffText(FakeLauncher.Call call) {
        return ((ProcessOutcome.Exited) echoingDiff(call)).stdout();
    }
}
