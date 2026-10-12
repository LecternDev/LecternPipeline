package tlm.dev.lectern.pipeline;

import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class PipelineConfigReader {

    static final String DEFAULT_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    static final String WORKBENCH_FOLDER_NAME = "LecternWorkbench";
    static final String GIT_EXECUTABLE = "git";
    static final String DEFAULT_HISTORY_START = "1.20.4";
    static final String USAGE = "usage: lectern-pipeline list | history | fetch <id> | extract <id> | normalize <id>"
            + " | publish <id> --data <git repository> [--diff-command <script>]"
            + " [--manifest-url=<url>] [--workbench=<folder>] [--java=<executable>] [--history-start=<id>]";

    private static final String LIST_COMMAND = "list";
    private static final String HISTORY_COMMAND = "history";
    private static final String FETCH_COMMAND = "fetch";
    private static final String EXTRACT_COMMAND = "extract";
    private static final String NORMALIZE_COMMAND = "normalize";
    private static final String PUBLISH_COMMAND = "publish";
    private static final String MANIFEST_URL_OPTION = "--manifest-url";
    private static final String WORKBENCH_OPTION = "--workbench";
    private static final String JAVA_OPTION = "--java";
    private static final String DATA_OPTION = "--data";
    private static final String DIFF_COMMAND_OPTION = "--diff-command";
    private static final String HISTORY_START_OPTION = "--history-start";
    private static final List<String> OPTIONS = List.of(MANIFEST_URL_OPTION, WORKBENCH_OPTION, JAVA_OPTION, DATA_OPTION, DIFF_COMMAND_OPTION, HISTORY_START_OPTION);
    private static final List<String> PUBLISH_ONLY_OPTIONS = List.of(DATA_OPTION, DIFF_COMMAND_OPTION);
    private static final List<String> WEB_SCHEMES = List.of("http", "https");
    private static final List<String> SCRIPT_EXTENSIONS = List.of(".bat", ".cmd", ".exe");
    private static final String WINDOWS_SCRIPT_EXTENSION = ".bat";

    private final Environment environment;

    public PipelineConfigReader(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    public PipelineConfig read(String[] args) {
        if (args.length == 0) {
            throw new UsageException(USAGE);
        }
        String word = args[0];
        if (LIST_COMMAND.equals(word) || HISTORY_COMMAND.equals(word)) {
            Map<String, String> options = options(args, 1);
            rejectPublishOnlyOptions(word, options);
            return config(LIST_COMMAND.equals(word) ? new Command.ListVersions() : new Command.History(), options, Optional.empty());
        }
        if (!List.of(FETCH_COMMAND, EXTRACT_COMMAND, NORMALIZE_COMMAND, PUBLISH_COMMAND).contains(word)) {
            throw new UsageException("Unknown command '" + word + "'\n" + USAGE);
        }
        VersionId id = versionId(args, word);
        Map<String, String> options = options(args, 2);
        if (!PUBLISH_COMMAND.equals(word)) {
            rejectPublishOnlyOptions(word, options);
        }
        return switch (word) {
            case FETCH_COMMAND -> config(new Command.Fetch(id), options, Optional.empty());
            case EXTRACT_COMMAND -> config(new Command.Extract(id), options, Optional.empty());
            case NORMALIZE_COMMAND -> config(new Command.Normalize(id), options, Optional.empty());
            default -> config(new Command.Publish(id, dataRepository(options)), options, diffCommand(options));
        };
    }

    private PipelineConfig config(Command command, Map<String, String> options, Optional<Path> diffCommand) {
        return new PipelineConfig(
                command,
                options.containsKey(MANIFEST_URL_OPTION) ? webUrl(options.get(MANIFEST_URL_OPTION)) : URI.create(DEFAULT_MANIFEST_URL),
                options.containsKey(WORKBENCH_OPTION) ? folder(WORKBENCH_OPTION, options.get(WORKBENCH_OPTION)) : defaultWorkbench(),
                options.containsKey(JAVA_OPTION) ? executable(options.get(JAVA_OPTION)) : environment.javaHome().resolve("bin").resolve("java"),
                Path.of(GIT_EXECUTABLE),
                diffCommand,
                historyStart(options),
                environment.tempDirectory());
    }

    private static void rejectPublishOnlyOptions(String word, Map<String, String> options) {
        for (String option : PUBLISH_ONLY_OPTIONS) {
            if (options.containsKey(option)) {
                throw new UsageException("The option " + option + " is only for the command publish, not for " + word + "\n" + USAGE);
            }
        }
    }

    private static VersionId historyStart(Map<String, String> options) {
        String text = options.getOrDefault(HISTORY_START_OPTION, DEFAULT_HISTORY_START);
        try {
            return new VersionId(text);
        } catch (IllegalArgumentException exception) {
            throw new UsageException("The first version of the history is not a version id: " + exception.getMessage() + "\n" + USAGE);
        }
    }

    private Optional<Path> diffCommand(Map<String, String> options) {
        Optional<String> text = options.containsKey(DIFF_COMMAND_OPTION) ? Optional.of(options.get(DIFF_COMMAND_OPTION)) : environment.diffCommand();
        return text.map(this::script);
    }

    private Path script(String text) {
        if (text.isBlank()) {
            throw new UsageException("The diff command needs a script path\n" + USAGE);
        }
        Path path = folder(DIFF_COMMAND_OPTION, text);
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean launchable = SCRIPT_EXTENSIONS.stream().anyMatch(name::endsWith);
        return environment.windows() && !launchable ? path.resolveSibling(path.getFileName() + WINDOWS_SCRIPT_EXTENSION) : path;
    }

    private Path dataRepository(Map<String, String> options) {
        if (!options.containsKey(DATA_OPTION)) {
            throw new UsageException("The command publish needs the option " + DATA_OPTION + " <git repository>\n" + USAGE);
        }
        return folder(DATA_OPTION, options.get(DATA_OPTION));
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
        int index = firstOption;
        while (index < args.length) {
            String argument = args[index];
            int equals = argument.indexOf('=');
            String name = equals < 0 ? argument : argument.substring(0, equals);
            if (!OPTIONS.contains(name)) {
                throw new UsageException("Unknown argument '" + argument + "'\n" + USAGE);
            }
            String value;
            if (equals >= 0) {
                value = argument.substring(equals + 1);
                index++;
            } else {
                if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
                    throw new UsageException("The option " + name + " needs a value\n" + USAGE);
                }
                value = args[index + 1];
                index += 2;
            }
            if (options.put(name, value) != null) {
                throw new UsageException("The option " + name + " is given twice\n" + USAGE);
            }
        }
        return options;
    }

    private Path defaultWorkbench() {
        Path parent = environment.currentDirectory().getParent();
        return (parent == null ? environment.currentDirectory() : parent).resolve(WORKBENCH_FOLDER_NAME);
    }

    private Path folder(String option, String text) {
        if (text.isBlank()) {
            throw new UsageException("The option " + option + " needs a folder\n" + USAGE);
        }
        try {
            return environment.currentDirectory().resolve(text).normalize();
        } catch (InvalidPathException exception) {
            throw new UsageException("The value of " + option + " is not a valid path: '" + text + "'\n" + USAGE);
        }
    }

    private static Path executable(String text) {
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
