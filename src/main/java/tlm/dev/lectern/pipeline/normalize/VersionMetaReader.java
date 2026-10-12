package tlm.dev.lectern.pipeline.normalize;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import tlm.dev.lectern.pipeline.json.JsonFormatException;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.json.JsonValue;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.model.VersionKind;

public final class VersionMetaReader {

    private final JsonTextReader json;

    public VersionMetaReader(JsonTextReader json) {
        this.json = Objects.requireNonNull(json, "json");
    }

    public VersionMeta read(VersionId id, Path file) {
        JsonValue.ObjectValue root = parse(id, file);
        requireSchema(id, file, root);
        VersionId found = versionId(id, file, root, "id");
        if (!found.equals(id)) {
            throw failure(id, file, "its id is '" + found.value() + "'");
        }
        return new VersionMeta(
                id,
                kind(id, file, root),
                instant(id, file, root),
                optionalVersion(id, file, root, "step_base"),
                optionalVersion(id, file, root, "release_base"),
                sha1(id, file, root),
                game(id, file, root));
    }

    private JsonValue.ObjectValue parse(VersionId id, Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            if (json.read(text) instanceof JsonValue.ObjectValue object) {
                return object;
            }
            throw failure(id, file, "it is not a JSON object");
        } catch (IOException | JsonFormatException exception) {
            throw new NormalizationException("Version " + id.value() + ": could not read " + file + ": " + exception.getMessage(), exception);
        }
    }

    private static void requireSchema(VersionId id, Path file, JsonValue.ObjectValue root) {
        String schema = root.get("schema_version").filter(JsonValue.NumberValue.class::isInstance)
                .map(value -> ((JsonValue.NumberValue) value).text()).orElse("none");
        if (!schema.equals(Integer.toString(VersionMetaWriter.SCHEMA_VERSION))) {
            throw failure(id, file, "its schema_version is " + schema + ", this pipeline reads " + VersionMetaWriter.SCHEMA_VERSION);
        }
    }

    private static VersionKind kind(VersionId id, Path file, JsonValue.ObjectValue root) {
        String label = string(id, file, root, "kind");
        return VersionKind.ofLabel(label).orElseThrow(() -> failure(id, file, "its kind '" + label + "' is not a known label"));
    }

    private static Instant instant(VersionId id, Path file, JsonValue.ObjectValue root) {
        String text = string(id, file, root, "release_time");
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException exception) {
            throw failure(id, file, "its release_time '" + text + "' is not an instant");
        }
    }

    private static Sha1 sha1(VersionId id, Path file, JsonValue.ObjectValue root) {
        String hex = string(id, file, root, "jar_sha1");
        try {
            return new Sha1(hex);
        } catch (IllegalArgumentException exception) {
            throw failure(id, file, "its jar_sha1 is not a SHA-1: " + exception.getMessage());
        }
    }

    private static Optional<JsonValue.ObjectValue> game(VersionId id, Path file, JsonValue.ObjectValue root) {
        Optional<JsonValue> value = root.get("game");
        if (value.isEmpty()) {
            return Optional.empty();
        }
        if (value.get() instanceof JsonValue.ObjectValue object) {
            return Optional.of(object);
        }
        throw failure(id, file, "its game is not an object");
    }

    private static Optional<VersionId> optionalVersion(VersionId id, Path file, JsonValue.ObjectValue root, String key) {
        JsonValue value = root.get(key).orElseThrow(() -> failure(id, file, "it has no " + key));
        return switch (value) {
            case JsonValue.NullValue _ -> Optional.empty();
            case JsonValue.StringValue text -> Optional.of(versionId(id, file, text.value(), key));
            default -> throw failure(id, file, "its " + key + " is neither a string nor null");
        };
    }

    private static VersionId versionId(VersionId id, Path file, JsonValue.ObjectValue root, String key) {
        return versionId(id, file, string(id, file, root, key), key);
    }

    private static VersionId versionId(VersionId id, Path file, String text, String key) {
        try {
            return new VersionId(text);
        } catch (IllegalArgumentException exception) {
            throw failure(id, file, "its " + key + " is not a version id: " + exception.getMessage());
        }
    }

    private static String string(VersionId id, Path file, JsonValue.ObjectValue root, String key) {
        JsonValue value = root.get(key).orElseThrow(() -> failure(id, file, "it has no " + key));
        if (value instanceof JsonValue.StringValue text) {
            return text.value();
        }
        throw failure(id, file, "its " + key + " is not a string");
    }

    private static NormalizationException failure(VersionId id, Path file, String reason) {
        return new NormalizationException("Version " + id.value() + ": the version sheet " + file + " is not usable: " + reason);
    }
}
