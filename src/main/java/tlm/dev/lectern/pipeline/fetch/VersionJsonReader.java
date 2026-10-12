package tlm.dev.lectern.pipeline.fetch;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import tlm.dev.lectern.pipeline.json.JsonFormatException;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.json.JsonValue;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class VersionJsonReader {

    private static final String DOWNLOADS = "downloads";
    private static final String SERVER = "server";

    private final JsonTextReader json;

    public VersionJsonReader(JsonTextReader json) {
        this.json = Objects.requireNonNull(json, "json");
    }

    public ServerDownload readServerDownload(VersionId id, byte[] versionJson) {
        String context = "Version " + id.value() + ": the version JSON";
        JsonValue.ObjectValue root = asObject(parse(context, versionJson), context);
        JsonValue.ObjectValue downloads = member(root, DOWNLOADS, context + " has no '" + DOWNLOADS + "' object");
        JsonValue.ObjectValue server = member(downloads, SERVER, context + " has no '" + DOWNLOADS + "." + SERVER + "' object");
        String where = context + " member '" + DOWNLOADS + "." + SERVER + "'";
        try {
            return new ServerDownload(
                    new Sha1(text(server, "sha1", where)),
                    Long.parseLong(number(server, "size", where)),
                    webUrl(text(server, "url", where)));
        } catch (IllegalArgumentException exception) {
            throw new VersionJsonFormatException(where + " is not valid: " + exception.getMessage(), exception);
        }
    }

    private JsonValue parse(String context, byte[] versionJson) {
        try {
            return json.read(new String(versionJson, StandardCharsets.UTF_8));
        } catch (JsonFormatException exception) {
            throw new VersionJsonFormatException(context + " is not valid JSON: " + exception.getMessage(), exception);
        }
    }

    private static JsonValue.ObjectValue asObject(JsonValue value, String context) {
        if (value instanceof JsonValue.ObjectValue object) {
            return object;
        }
        throw new VersionJsonFormatException(context + " must be a JSON object");
    }

    private static JsonValue.ObjectValue member(JsonValue.ObjectValue parent, String name, String missing) {
        return parent.get(name)
                .flatMap(value -> value instanceof JsonValue.ObjectValue object ? Optional.of(object) : Optional.empty())
                .orElseThrow(() -> new VersionJsonFormatException(missing));
    }

    private static String text(JsonValue.ObjectValue object, String name, String where) {
        return object.get(name)
                .flatMap(value -> value instanceof JsonValue.StringValue string ? Optional.of(string.value()) : Optional.empty())
                .orElseThrow(() -> new VersionJsonFormatException(where + " has no string '" + name + "'"));
    }

    private static String number(JsonValue.ObjectValue object, String name, String where) {
        return object.get(name)
                .flatMap(value -> value instanceof JsonValue.NumberValue number ? Optional.of(number.text()) : Optional.empty())
                .orElseThrow(() -> new VersionJsonFormatException(where + " has no number '" + name + "'"));
    }

    private static URI webUrl(String text) {
        URI url = URI.create(text);
        if (url.getHost() == null || url.getScheme() == null || !(url.getScheme().equals("https") || url.getScheme().equals("http"))) {
            throw new IllegalArgumentException("the url must be an http or https url with a host: '" + text + "'");
        }
        return url;
    }
}
