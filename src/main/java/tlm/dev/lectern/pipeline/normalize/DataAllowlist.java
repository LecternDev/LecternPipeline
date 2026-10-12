package tlm.dev.lectern.pipeline.normalize;

import java.util.List;
import java.util.Optional;

public final class DataAllowlist {

    public enum Layout {
        GENERATED,
        NORMALIZED
    }

    public sealed interface Verdict {

        record Keep() implements Verdict {
        }

        record Ignore() implements Verdict {
        }

        record Reject(String reason) implements Verdict {
        }
    }

    static final String META_FILE = "meta.json";
    static final String REPORTS_FOLDER = "reports";
    static final String DATA_FOLDER = "data";
    static final String CACHE_FOLDER = ".cache";

    private static final List<String> EXTENSIONS = List.of(".json", ".mcmeta");

    private DataAllowlist() {
    }

    public static Verdict check(Layout layout, String relativePath) {
        Optional<String> unsafe = unsafeReason(relativePath);
        if (unsafe.isPresent()) {
            return new Verdict.Reject(unsafe.get());
        }
        String[] segments = relativePath.split("/");
        String top = segments[0];
        if (layout == Layout.GENERATED && top.equals(CACHE_FOLDER)) {
            return new Verdict.Ignore();
        }
        if (layout == Layout.NORMALIZED && relativePath.equals(META_FILE)) {
            return new Verdict.Keep();
        }
        if (!top.equals(REPORTS_FOLDER) && !top.equals(DATA_FOLDER)) {
            return new Verdict.Reject(layout == Layout.GENERATED
                    ? "it is outside reports/ and data/"
                    : "it is outside reports/ and data/ and is not meta.json");
        }
        if (segments.length < 2) {
            return new Verdict.Reject("it is a file where the folder " + top + "/ is expected");
        }
        if (!hasAllowedExtension(segments[segments.length - 1])) {
            return new Verdict.Reject("its extension is not .json or .mcmeta");
        }
        return new Verdict.Keep();
    }

    private static boolean hasAllowedExtension(String name) {
        return EXTENSIONS.stream().anyMatch(extension -> name.endsWith(extension) && name.length() > extension.length());
    }

    private static Optional<String> unsafeReason(String relativePath) {
        if (relativePath.isEmpty()) {
            return Optional.of("its path is empty");
        }
        if (relativePath.startsWith("/") || relativePath.indexOf('\\') >= 0) {
            return Optional.of("its path is absolute or uses a backslash");
        }
        if (relativePath.chars().anyMatch(character -> character < 0x20 || character == 0x7F)) {
            return Optional.of("its path contains a control character");
        }
        for (String segment : relativePath.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return Optional.of("its path leaves the folder or has an empty or relative segment");
            }
            if (segment.indexOf(':') >= 0) {
                return Optional.of("its path has a drive or stream marker");
            }
        }
        return Optional.empty();
    }
}
