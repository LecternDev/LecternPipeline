package tlm.dev.lectern.pipeline.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdkProcessLauncherTest {

    private static final String JAVA = Path.of(System.getProperty("java.home"), "bin", "java").toString();

    private final JdkProcessLauncher launcher = new JdkProcessLauncher();

    @TempDir
    Path workFolder;

    private Path program(String name, String body) throws IOException {
        String className = name.substring(0, name.indexOf('.'));
        Path source = workFolder.resolve(name);
        Files.writeString(source, "public class " + className + " {\n    public static void main(String[] args) throws Exception {\n" + body + "\n    }\n}\n");
        return source;
    }

    @Test
    void exitCodeZeroAndTheOutputOfTheProcessAreReturned() {
        ProcessOutcome outcome = launcher.launch(List.of(JAVA, "-version"), workFolder, Duration.ofSeconds(60));
        ProcessOutcome.Exited exited = assertInstanceOf(ProcessOutcome.Exited.class, outcome);
        assertEquals(0, exited.exitCode());
        assertTrue(exited.stderrTail().contains("version"), exited.stderrTail());
    }

    @Test
    void nonZeroExitCodeIsReturnedWithTheEndOfStderr() {
        ProcessOutcome outcome = launcher.launch(List.of(JAVA, "-cp", workFolder.toString(), "InventedMissingClass"), workFolder, Duration.ofSeconds(60));
        ProcessOutcome.Exited exited = assertInstanceOf(ProcessOutcome.Exited.class, outcome);
        assertEquals(1, exited.exitCode());
        assertTrue(exited.stderrTail().contains("InventedMissingClass"), exited.stderrTail());
    }

    @Test
    void processRunsInTheGivenWorkingDirectory() throws IOException {
        Path source = program("WhereAmI.java", "        System.out.println(\"cwd=\" + System.getProperty(\"user.dir\"));");
        ProcessOutcome.Exited exited = assertInstanceOf(ProcessOutcome.Exited.class,
                launcher.launch(List.of(JAVA, source.toString()), workFolder, Duration.ofSeconds(60)));
        assertEquals(0, exited.exitCode());
        assertTrue(exited.stdoutTail().contains("cwd=" + workFolder), exited.stdoutTail());
    }

    @Test
    void onlyTheLastLinesOfEachStreamAreKept() throws IOException {
        Path source = program("Chatty.java", "        for (int line = 1; line <= 100; line++) {\n            System.err.println(\"error line \" + line);\n            System.out.println(\"out line \" + line);\n        }");
        ProcessOutcome.Exited exited = assertInstanceOf(ProcessOutcome.Exited.class,
                launcher.launch(List.of(JAVA, source.toString()), workFolder, Duration.ofSeconds(60)));
        List<String> stderrLines = exited.stderrTail().lines().toList();
        assertEquals(15, stderrLines.size());
        assertEquals("error line 100", stderrLines.get(14));
        assertEquals("error line 86", stderrLines.get(0));
        assertEquals("out line 100", exited.stdoutTail().lines().toList().get(14));
    }

    @Test
    void processThatRunsTooLongIsStoppedAndReportedAsTimedOut() throws IOException {
        Path source = program("Sleeper.java", "        System.err.println(\"started\");\n        Thread.sleep(120_000);");
        long start = System.nanoTime();
        ProcessOutcome outcome = launcher.launch(List.of(JAVA, source.toString()), workFolder, Duration.ofSeconds(4));
        long elapsedSeconds = Duration.ofNanos(System.nanoTime() - start).toSeconds();
        ProcessOutcome.TimedOut timedOut = assertInstanceOf(ProcessOutcome.TimedOut.class, outcome);
        assertEquals(Duration.ofSeconds(4), timedOut.timeout());
        assertTrue(elapsedSeconds < 60, "took " + elapsedSeconds + " s");
    }

    @Test
    void missingExecutableIsALaunchFailureNamingIt() {
        String missing = workFolder.resolve("no-such-java").toString();
        ProcessLaunchException failure = assertThrows(ProcessLaunchException.class,
                () -> launcher.launch(List.of(missing, "-version"), workFolder, Duration.ofSeconds(10)));
        assertTrue(failure.getMessage().contains("no-such-java"), failure.getMessage());
        assertTrue(failure.getCause() instanceof IOException, String.valueOf(failure.getCause()));
    }

    @Test
    void interruptedThreadIsALaunchFailureAndKeepsItsFlag() throws IOException {
        Path source = program("Sleeper.java", "        Thread.sleep(120_000);");
        Thread.currentThread().interrupt();
        try {
            assertThrows(ProcessLaunchException.class, () -> launcher.launch(List.of(JAVA, source.toString()), workFolder, Duration.ofSeconds(30)));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
