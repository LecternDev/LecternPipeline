package tlm.dev.lectern.pipeline.normalize;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import tlm.dev.lectern.pipeline.json.JsonFormatException;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.json.JsonValue;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class GameInfoReader {

    static final List<String> KEYS = List.of("id", "world_version", "protocol_version", "pack_version", "build_time", "stable");

    private static final String VERSION_JSON_ENTRY = "version.json";
    private static final int MAX_BYTES = 1024 * 1024;

    private final JsonTextReader json;

    public GameInfoReader(JsonTextReader json) {
        this.json = Objects.requireNonNull(json, "json");
    }

    public Optional<JsonValue.ObjectValue> read(VersionId id, Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(VERSION_JSON_ENTRY);
            if (entry == null) {
                return Optional.empty();
            }
            return Optional.of(pick(id, parse(id, readBounded(id, zip, entry))));
        } catch (IOException exception) {
            throw new NormalizationException("Version " + id.value() + ": could not read " + VERSION_JSON_ENTRY + " in " + jar
                    + ": " + exception.getMessage(), exception);
        }
    }

    private static byte[] readBounded(VersionId id, ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream stream = zip.getInputStream(entry)) {
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new NormalizationException("Version " + id.value() + ": " + VERSION_JSON_ENTRY + " in the jar is larger than " + MAX_BYTES + " bytes");
            }
            return bytes;
        }
    }

    private JsonValue parse(VersionId id, byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return json.read(text);
        } catch (CharacterCodingException | JsonFormatException exception) {
            throw new NormalizationException("Version " + id.value() + ": " + VERSION_JSON_ENTRY + " in the jar is not valid JSON: "
                    + exception.getMessage(), exception);
        }
    }

    private static JsonValue.ObjectValue pick(VersionId id, JsonValue parsed) {
        if (!(parsed instanceof JsonValue.ObjectValue object)) {
            throw new NormalizationException("Version " + id.value() + ": " + VERSION_JSON_ENTRY + " in the jar must be a JSON object");
        }
        Map<String, JsonValue> picked = new LinkedHashMap<>();
        for (String key : KEYS) {
            object.get(key).ifPresent(value -> picked.put(key, value));
        }
        return new JsonValue.ObjectValue(picked);
    }
}
