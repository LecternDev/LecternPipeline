package tlm.dev.lectern.pipeline.extract;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class JarEraDetector {

    private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF";
    private static final String BUNDLER_MAIN_CLASS = "net.minecraft.bundler.Main";
    private static final String DATA_MAIN_ENTRY = "net/minecraft/data/Main.class";
    private static final String MAIN_CLASS_HEADER = "Main-Class";
    private static final int MAX_MANIFEST_CHARS = 64 * 1024;

    public JarEra detect(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry manifest = zip.getEntry(MANIFEST_ENTRY);
            if (manifest == null) {
                throw new UnsupportedJarException(jar, "it has no " + MANIFEST_ENTRY);
            }
            Optional<String> mainClass = mainClassOf(readManifestStart(zip, manifest));
            if (mainClass.filter(BUNDLER_MAIN_CLASS::equals).isPresent()) {
                return new JarEra.Bundler();
            }
            if (zip.getEntry(DATA_MAIN_ENTRY) != null) {
                return new JarEra.Simple();
            }
            throw new UnsupportedJarException(jar,
                    "its Main-Class is " + mainClass.orElse("absent") + " and it has no " + DATA_MAIN_ENTRY);
        } catch (IOException exception) {
            throw new UnsupportedJarException(jar, "it cannot be read as a zip archive (" + exception.getMessage() + ")", exception);
        }
    }

    private static String readManifestStart(ZipFile zip, ZipEntry manifest) throws IOException {
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[4096];
        try (Reader reader = new InputStreamReader(zip.getInputStream(manifest), StandardCharsets.UTF_8)) {
            for (int read = reader.read(buffer); read >= 0 && text.length() < MAX_MANIFEST_CHARS; read = reader.read(buffer)) {
                text.append(buffer, 0, read);
            }
        }
        return text.toString();
    }

    private static Optional<String> mainClassOf(String manifestStart) {
        StringBuilder current = new StringBuilder();
        for (String line : manifestStart.split("\r\n|\n|\r", -1)) {
            if (line.isEmpty()) {
                break;
            }
            if (line.startsWith(" ")) {
                current.append(line, 1, line.length());
                continue;
            }
            Optional<String> value = mainClassValue(current.toString());
            if (value.isPresent()) {
                return value;
            }
            current.setLength(0);
            current.append(line);
        }
        return mainClassValue(current.toString());
    }

    private static Optional<String> mainClassValue(String header) {
        int colon = header.indexOf(':');
        if (colon > 0 && header.substring(0, colon).equalsIgnoreCase(MAIN_CLASS_HEADER)) {
            return Optional.of(header.substring(colon + 1).strip());
        }
        return Optional.empty();
    }
}
