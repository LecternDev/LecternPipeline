package tlm.dev.lectern.pipeline;

import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class PipelineConfigReader {

    static final String DEFAULT_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    static final String WORKBENCH_FOLDER_NAME = "LecternWorkbench";
    static final String USAGE = "usage: lectern-pipeline list | fetch <id> | extract <id>"
            + " [--manifest-url=<url>] [--workbench=<folder>] [--java=<executable>]";

    private static final String LIST_COMMAND = "list";
    private static final String FETCH_COMMAND = "fetch";
    private static final String EXTRACT_COMMAND = "extract";
    private static final String MANIFEST_URL_OPTION = "--manifest-url";
    private static final String WORKBENCH_OPTION = "--workbench";
    private static final String JAVA_OPTION = "--java";
    private static final List<String> WEB_SCHEMES = List.of("http", "https");

    private final Environment environment;

    public PipelineConfigReader(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    public PipelineConfig read(String[] args) {
        if (args.length == 0) {
            throw new UsageException(USAGE);
        }
        int firstOption = 1;
        Command command;
        switch (args[0]) {
            case LIST_COMMAND -> command = new Command.ListVersions();
            case FETCH_COMMAND -> {
                command = new Command.Fetch(versionId(args, FETCH_COMMAND));
                firstOption = 2;
            }
            case EXTRACT_COMMAND -> {
                command = new Command.Extract(versionId(args, EXTRACT_COMMAND));
                firstOption = 2;
            }
            default -> throw new UsageException("Unknown command '" + args[0] + "'\n" + USAGE);
        }
        Map<String, String> options = options(args, firstOption);
        return new PipelineConfig(
                command,
                options.containsKey(MANIFEST_URL_OPTION) ? webUrl(options.get(MANIFEST_URL_OPTION)) : URI.create(DEFAULT_MANIFEST_URL),
                options.containsKey(WORKBENCH_OPTION) ? workbench(options.get(WORKBENCH_OPTION)) : defaultWorkbench(),
                options.containsKey(JAVA_OPTION) ? java(options.get(JAVA_OPTION)) : environment.javaHome().resolve("bin").resolve("java"),
                environment.tempDirectory());
    }

    private static VersionId versionId(String[] args, String command) {
        if (args.length < 2 || args[1].startsWith("--")) {
            throw new UsageException("The command '" + command + "' needs a version id\n" + USAGE);
        }
        try {
            return new VersionId(args[1]);
        } catch (IllegalArgumentException exception) {
            throw new UsageException("Not a valid version id: " + exception.getMessage() + "\n" + USAGE);
        }
    }

    private static Map<String, String> options(String[] args, int firstOption) {
        Map<String, String> options = new HashMap<>();
        for (int index = firstOption; index < args.length; index++) {
            int equals = args[index].indexOf('=');
            String name = equals < 0 ? args[index] : args[index].substring(0, equals);
            if (equals < 0 || !List.of(MANIFEST_URL_OPTION, WORKBENCH_OPTION, JAVA_OPTION).contains(name)) {
                throw new UsageException("Unknown argument '" + args[index] + "'\n" + USAGE);
            }
            if (options.put(name, args[index].substring(equals + 1)) != null) {
                throw new UsageException("The option " + name + " is given twice\n" + USAGE);
            }
        }
        return options;
    }

    private Path defaultWorkbench() {
        Path parent = environment.currentDirectory().getParent();
        return (parent == null ? environment.currentDirectory() : parent).resolve(WORKBENCH_FOLDER_NAME);
    }

    private Path workbench(String text) {
        if (text.isBlank()) {
            throw new UsageException("The option " + WORKBENCH_OPTION + " needs a folder\n" + USAGE);
        }
        try {
            return environment.currentDirectory().resolve(text).normalize();
        } catch (InvalidPathException exception) {
            throw new UsageException("The workbench folder is not a valid path: '" + text + "'\n" + USAGE);
        }
    }

    private static Path java(String text) {
        if (text.isBlank()) {
            throw new UsageException("The option " + JAVA_OPTION + " needs an executable\n" + USAGE);
        }
        try {
            return Path.of(text);
        } catch (InvalidPathException exception) {
            throw new UsageException("The java executable is not a valid path: '" + text + "'\n" + USAGE);
        }
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
