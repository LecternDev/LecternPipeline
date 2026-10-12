package tlm.dev.lectern.pipeline;

import java.net.URI;
import java.util.List;

public final class PipelineConfigReader {

    static final String DEFAULT_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    static final String USAGE = "usage: lectern-pipeline list [--manifest-url=<url>]";

    private static final String LIST_COMMAND = "list";
    private static final String MANIFEST_URL_OPTION = "--manifest-url=";
    private static final List<String> WEB_SCHEMES = List.of("http", "https");

    public PipelineConfig read(String[] args) {
        if (args.length == 0) {
            throw new UsageException(USAGE);
        }
        if (!LIST_COMMAND.equals(args[0])) {
            throw new UsageException("Unknown command '" + args[0] + "'\n" + USAGE);
        }
        URI manifestUrl = URI.create(DEFAULT_MANIFEST_URL);
        boolean manifestUrlGiven = false;
        for (int index = 1; index < args.length; index++) {
            if (!args[index].startsWith(MANIFEST_URL_OPTION)) {
                throw new UsageException("Unknown argument '" + args[index] + "'\n" + USAGE);
            }
            if (manifestUrlGiven) {
                throw new UsageException("The option --manifest-url is given twice\n" + USAGE);
            }
            manifestUrl = webUrl(args[index].substring(MANIFEST_URL_OPTION.length()));
            manifestUrlGiven = true;
        }
        return new PipelineConfig(manifestUrl);
    }

    private static URI webUrl(String text) {
        URI url;
        try {
            url = URI.create(text);
        } catch (IllegalArgumentException exception) {
            throw new UsageException("The manifest url is not valid: '" + text + "'\n" + USAGE);
        }
        if (url.getScheme() == null || !WEB_SCHEMES.contains(url.getScheme()) || url.getHost() == null) {
            throw new UsageException("The manifest url must be an http or https url with a host: '" + text + "'\n" + USAGE);
        }
        return url;
    }
}
