package tlm.dev.lectern.pipeline.normalize;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import tlm.dev.lectern.pipeline.files.DraftFolders;
import tlm.dev.lectern.pipeline.json.JsonFormatException;
import tlm.dev.lectern.pipeline.json.JsonValue;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class NormalizationService {

    private final NormalizationTools tools;
    private final VersionMetaWriter metaWriter;
    private final NormalizationSettings settings;
    private final Clock clock;

    public NormalizationService(NormalizationTools tools, VersionMetaWriter metaWriter, NormalizationSettings settings, Clock clock) {
        this.tools = Objects.requireNonNull(tools, "tools");
        this.metaWriter = Objects.requireNonNull(metaWriter, "metaWriter");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void requireFreshOutput(VersionId id) {
        Path output = outputOf(id);
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw new NormalizationException("Version " + id.value() + ": the folder " + output
                    + " already exists. Nothing was changed, remove it yourself to normalize again");
        }
    }

    public NormalizationResult normalize(VersionMeta meta) {
        VersionId id = meta.id();
        requireFreshOutput(id);
        Path source = settings.generatedRoot().resolve(id.value());
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new NormalizationException("Version " + id.value() + ": there is no generated folder at " + source + ", extract the version first");
        }
        List<String> files = tools.scanner().scan(id, source, DataAllowlist.Layout.GENERATED);
        Path output = outputOf(id);
        Path draft = DraftFolders.draftOf(output);
        clearDraft(draft);
        Instant start = clock.instant();
        long bytesRead = 0;
        long bytesWritten = 0;
        int rewritten = 0;
        for (String relative : files) {
            byte[] original = read(source.resolve(relative));
            byte[] canonical = canonicalize(id, relative, original);
            write(draft.resolve(relative), canonical);
            bytesRead += original.length;
            bytesWritten += canonical.length;
            if (!Arrays.equals(original, canonical)) {
                rewritten++;
            }
        }
        write(draft.resolve(DataAllowlist.META_FILE), metaWriter.toBytes(meta));
        promote(id, draft, output);
        return new NormalizationResult(id, output, files, bytesRead, bytesWritten, rewritten, Duration.between(start, clock.instant()));
    }

    public byte[] canonicalize(VersionId id, String relative, byte[] original) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(original))
                    .toString();
            JsonValue value = tools.reader().read(text);
            return tools.writer().toBytes(value);
        } catch (CharacterCodingException | JsonFormatException exception) {
            throw new NormalizationException("Version " + id.value() + ": the file '" + relative + "' is not valid UTF-8 JSON: "
                    + exception.getMessage() + ". Nothing was published, the draft folder was left as it is", exception);
        }
    }

    private Path outputOf(VersionId id) {
        return settings.normalizedRoot().resolve(id.value());
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }

    private static void write(Path file, byte[] bytes) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write " + file, exception);
        }
    }

    private static void clearDraft(Path draft) {
        try {
            DraftFolders.clear(draft);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not replace the draft " + draft + " left by a previous normalization", exception);
        }
    }

    private static void promote(VersionId id, Path draft, Path output) {
        try {
            DraftFolders.promote(draft, output);
        } catch (FileAlreadyExistsException exception) {
            throw new NormalizationException("Version " + id.value() + ": the folder " + output + " appeared during the run and was not replaced");
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not rename " + draft + " to " + output, exception);
        }
    }
}
