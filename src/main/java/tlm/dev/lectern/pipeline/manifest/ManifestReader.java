package tlm.dev.lectern.pipeline.manifest;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import tlm.dev.lectern.pipeline.json.JsonFormatException;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.json.JsonValue;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class ManifestReader {

    private final JsonTextReader json;

    public ManifestReader(JsonTextReader json) {
        this.json = Objects.requireNonNull(json, "json");
    }

    public List<ManifestEntry> read(String manifestText) {
        JsonValue.ObjectValue root = asObject(parse(manifestText), "The manifest");
        JsonValue versions = root.get("versions")
                .orElseThrow(() -> new ManifestFormatException("The manifest has no 'versions' member"));
        if (!(versions instanceof JsonValue.ArrayValue array)) {
            throw new ManifestFormatException("The manifest member 'versions' must be an array");
        }
        List<ManifestEntry> entries = new ArrayList<>();
        Set<VersionId> seen = new HashSet<>();
        for (int index = 0; index < array.elements().size(); index++) {
            ManifestEntry entry = readEntry(index, array.elements().get(index));
            if (!seen.add(entry.id())) {
                throw new ManifestFormatException("Manifest entry " + index + " repeats the version id '" + entry.id().value() + "'");
            }
            entries.add(entry);
        }
        return List.copyOf(entries);
    }

    private JsonValue parse(String manifestText) {
        try {
            return json.read(manifestText);
        } catch (JsonFormatException exception) {
            throw new ManifestFormatException("The manifest is not valid JSON: " + exception.getMessage(), exception);
        }
    }

    private static ManifestEntry readEntry(int index, JsonValue value) {
        JsonValue.ObjectValue object = asObject(value, "Manifest entry " + index);
        String context = "Manifest entry " + index;
        String rawId = text(object, "id", context);
        String described = context + " ('" + rawId + "')";
        try {
            return new ManifestEntry(
                    new VersionId(rawId),
                    text(object, "type", described),
                    URI.create(text(object, "url", described)),
                    OffsetDateTime.parse(text(object, "releaseTime", described)).toInstant(),
                    new Sha1(text(object, "sha1", described)));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new ManifestFormatException(described + ": " + exception.getMessage(), exception);
        }
    }

    private static JsonValue.ObjectValue asObject(JsonValue value, String what) {
        if (value instanceof JsonValue.ObjectValue object) {
            return object;
        }
        throw new ManifestFormatException(what + " must be a JSON object");
    }

    private static String text(JsonValue.ObjectValue object, String name, String context) {
        return object.get(name)
                .flatMap(ManifestReader::asString)
                .orElseThrow(() -> new ManifestFormatException(context + " has no string member '" + name + "'"));
    }

    private static Optional<String> asString(JsonValue value) {
        if (value instanceof JsonValue.StringValue string) {
            return Optional.of(string.value());
        }
        return Optional.empty();
    }
}
