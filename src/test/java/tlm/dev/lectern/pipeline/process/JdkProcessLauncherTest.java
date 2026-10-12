package tlm.dev.lectern.pipeline.process;

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
        assertTrue(exited.stderr().contains("version"), exited.stderr());
    }

    @Test
    void nonZeroExitCodeIsReturnedWithStderr() {
        ProcessOutcome outcome = launcher.launch(List.of(JAVA, "-cp", workFolder.toString(), "InventedMissingClass"), workFolder, Duration.ofSeconds(60));
        ProcessOutcome.Exited exited = assertInstanceOf(ProcessOutcome.Exited.class, outcome);
        assertEquals(1, exited.exitCode());
        assertTrue(exited.stderr().contains("InventedMissingClass"), exited.stderr());
    }

    @Test
    void processRunsInTheGivenWorkingDirectory() throws IOException {
        Path source = program("WhereAmI.java", "        System.out.println(\"cwd=\" + System.getProperty(\"user.dir\"));");
        ProcessOutcome.Exited exited = assertInstanceOf(ProcessOutcome.Exited.class,
                launcher.launch(List.of(JAVA, source.toString()), workFolder, Duration.ofSeconds(60)));
        assertEquals(0, exited.exitCode());
        assertTrue(exited.stdout().contains("cwd=" + workFolder), exited.stdout());
    }

    @Test
    void wholeOutputOfBothStreamsIsReturnedAndTailKeepsTheLastLines() throws IOException {
        Path source = program("Chatty.java", "        for (int line = 1; line <= 100; line++) {\n            System.err.println(\"error line \" + line);\n            System.out.println(\"out line \" + line);\n        }");
        ProcessOutcome.Exited exited = assertInstanceOf(ProcessOutcome.Exited.class,
                launcher.launch(List.of(JAVA, source.toString()), workFolder, Duration.ofSeconds(60)));
        assertEquals(100, exited.stdout().lines().count());
        assertEquals("out line 1", exited.stdout().lines().findFirst().orElseThrow());
        List<String> tail = ProcessOutcome.tail(exited.stderr(), 15).lines().toList();
        assertEquals(15, tail.size());
        assertEquals("error line 86", tail.get(0));
        assertEquals("error line 100", tail.get(14));
    }

    @Test
    void outputBeyondTheLimitKeepsItsEndAndSaysSo() throws IOException {
        Path source = program("Verbose.java", "        for (int line = 1; line <= 2000; line++) {\n            System.out.println(\"out line \" + line);\n        }");
        JdkProcessLauncher small = new JdkProcessLauncher(1000);
        ProcessOutcome.Exited exited = assertInstanceOf(ProcessOutcome.Exited.class,
                small.launch(List.of(JAVA, source.toString()), workFolder, Duration.ofSeconds(60)));
        assertTrue(exited.stdout().startsWith("[earlier output dropped]\n"), exited.stdout());
        assertTrue(exited.stdout().endsWith("out line 2000\n") || exited.stdout().endsWith("out line 2000\r\n"), exited.stdout());
        assertTrue(exited.stdout().length() < 1100, "length " + exited.stdout().length());
    }

    @Test
    void tailOfShortTextIsTheWholeText() {
        assertEquals("a\nb", ProcessOutcome.tail("a\nb\n", 15));
        assertEquals("", ProcessOutcome.tail("", 15));
    }

    @Test
    void limitMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new JdkProcessLauncher(0));
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
