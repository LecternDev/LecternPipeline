package tlm.dev.lectern.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SeamRules {

    static final String ROOT = "tlm.dev.lectern.pipeline.";

    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+)", Pattern.MULTILINE);
    private static final List<String> JDK_PREFIXES = List.of("java.", "javax.");
    private static final Set<String> FAMILIES = Set.of(
            "model", "json", "http", "process", "files", "manifest", "fetch", "extract", "normalize", "publish");
    private static final Map<String, String> THIRD_PARTY_OWNERS = Map.of("tools.jackson", "json");

    private record Seam(String from, Set<String> forbidden) {
    }

    private static final List<Seam> SEAMS = List.of(
            new Seam("model", Set.of("json", "http", "process", "files", "manifest", "fetch", "extract", "normalize", "publish")),
            new Seam("json", Set.of("model", "http", "process", "files", "manifest", "fetch", "extract", "normalize", "publish")),
            new Seam("http", Set.of("model", "json", "process", "files", "manifest", "fetch", "extract", "normalize", "publish")),
            new Seam("process", Set.of("model", "json", "http", "files", "manifest", "fetch", "extract", "normalize", "publish")),
            new Seam("files", Set.of("model", "json", "http", "process", "manifest", "fetch", "extract", "normalize", "publish")),
            new Seam("manifest", Set.of("fetch", "extract", "normalize", "publish")),
            new Seam("fetch", Set.of("manifest", "extract", "normalize", "publish")),
            new Seam("extract", Set.of("manifest", "fetch", "normalize", "publish")),
            new Seam("normalize", Set.of("manifest", "fetch", "extract", "publish")),
            new Seam("publish", Set.of("manifest", "fetch", "extract", "normalize")));

    private SeamRules() {
    }

    static List<String> violations(String relativePath, String source) {
        String family = familyOf(relativePath);
        List<String> found = new ArrayList<>();
        if (!family.isEmpty() && !FAMILIES.contains(family)) {
            found.add(relativePath + ": undeclared family " + family);
        }
        for (String imported : imports(SourceScanner.scan(source).code())) {
            checkImport(relativePath, family, imported, found);
        }
        return found;
    }

    private static String familyOf(String relativePath) {
        int separator = relativePath.indexOf('/');
        return separator < 0 ? "" : relativePath.substring(0, separator);
    }

    private static List<String> imports(String code) {
        List<String> imports = new ArrayList<>();
        Matcher matcher = IMPORT.matcher(code);
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }
        return imports;
    }

    private static void checkImport(String relativePath, String family, String imported, List<String> found) {
        if (JDK_PREFIXES.stream().anyMatch(imported::startsWith)) {
            return;
        }
        if (imported.startsWith(ROOT)) {
            checkOwnImport(relativePath, family, imported, found);
        } else {
            checkThirdPartyImport(relativePath, family, imported, found);
        }
    }

    private static void checkOwnImport(String relativePath, String family, String imported, List<String> found) {
        String remainder = imported.substring(ROOT.length());
        int dot = remainder.indexOf('.');
        String first = dot < 0 ? remainder : remainder.substring(0, dot);
        if (!first.isEmpty() && Character.isUpperCase(first.charAt(0))) {
            if (!family.isEmpty()) {
                found.add(relativePath + ": " + family + " -> root (" + imported + ")");
            }
            return;
        }
        if (!FAMILIES.contains(first)) {
            found.add(relativePath + ": undeclared family " + first + " (" + imported + ")");
            return;
        }
        for (Seam seam : SEAMS) {
            if (seam.from().equals(family) && seam.forbidden().contains(first)) {
                found.add(relativePath + ": " + family + " -> " + first + " (" + imported + ")");
            }
        }
    }

    private static void checkThirdPartyImport(String relativePath, String family, String imported, List<String> found) {
        for (Map.Entry<String, String> owner : THIRD_PARTY_OWNERS.entrySet()) {
            if (imported.startsWith(owner.getKey() + ".")) {
                if (!owner.getValue().equals(family)) {
                    found.add(relativePath + ": " + owner.getKey() + " outside " + owner.getValue() + " (" + imported + ")");
                }
                return;
            }
        }
        found.add(relativePath + ": undeclared third-party import (" + imported + ")");
    }
}
