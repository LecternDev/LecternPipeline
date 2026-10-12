package tlm.dev.lectern.pipeline.extract;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class ExtractionService {

    private static final System.Logger LOGGER = System.getLogger(ExtractionService.class.getName());
    private static final String WORK_FOLDER_PREFIX = "lectern-extract-";

    private final ProcessLauncher launcher;
    private final JarEraDetector detector;
    private final ExtractionSettings settings;
    private final Clock clock;

    public ExtractionService(ProcessLauncher launcher, JarEraDetector detector, ExtractionSettings settings, Clock clock) {
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.detector = Objects.requireNonNull(detector, "detector");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void requireFreshOutput(VersionId id) {
        Path output = settings.generatedRoot().resolve(id.value());
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw new OutputExistsException(id, output);
        }
    }

    public ExtractionResult extract(VersionId id, Path jar) {
        requireFreshOutput(id);
        Path output = settings.generatedRoot().resolve(id.value());
        JarEra era = detector.detect(jar);
        List<String> command = GeneratorCommandPlanner.plan(era, settings.java(), jar.toAbsolutePath(), output.toAbsolutePath());
        ensureFolder(settings.generatedRoot());
        Path workFolder = newWorkFolder();
        try {
            Instant start = clock.instant();
            ProcessOutcome outcome = launch(id, command, workFolder);
            Duration duration = Duration.between(start, clock.instant());
            return switch (outcome) {
                case ProcessOutcome.Exited exited when exited.exitCode() == 0 -> result(id, output, duration);
                case ProcessOutcome.Exited exited -> throw GeneratorFailureException.exited(id, output, exited);
                case ProcessOutcome.TimedOut timedOut -> throw GeneratorFailureException.timedOut(id, output, timedOut);
            };
        } finally {
            deleteWorkFolder(workFolder);
        }
    }

    private ProcessOutcome launch(VersionId id, List<String> command, Path workFolder) {
        try {
            return launcher.launch(command, workFolder, settings.timeout());
        } catch (ProcessLaunchException exception) {
            throw GeneratorFailureException.notStarted(id, exception);
        }
    }

    private ExtractionResult result(VersionId id, Path output, Duration duration) {
        if (!Files.isDirectory(output)) {
            throw GeneratorFailureException.noOutput(id, output);
        }
        return new ExtractionResult(id, output, new TreeSummarizer().summarize(output), duration);
    }

    private Path newWorkFolder() {
        ensureFolder(settings.scratchRoot());
        try {
            return Files.createTempDirectory(settings.scratchRoot(), WORK_FOLDER_PREFIX);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not create a work folder under " + settings.scratchRoot(), exception);
        }
    }

    private static void ensureFolder(Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not create the folder " + folder, exception);
        }
    }

    private static void deleteWorkFolder(Path folder) {
        try {
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not delete the work folder " + folder + ": " + exception);
        }
    }
}
