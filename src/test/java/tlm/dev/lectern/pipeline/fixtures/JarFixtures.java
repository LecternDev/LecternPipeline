package tlm.dev.lectern.pipeline.fixtures;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class JarFixtures {

    private JarFixtures() {
    }

    public static Path bundlerJar(Path file) {
        return write(file, entries(
                "META-INF/MANIFEST.MF", text("Manifest-Version: 1.0\r\nMain-Class: net.minecraft.bundler.Main\r\nBundler-Format: 1.0\r\n\r\n"),
                "META-INF/versions.list", text("0000\t99.1\t99.1/server-99.1.jar\n"),
                "version.json", text("{\"id\": \"99.1\"}")));
    }

    public static Path simpleJar(Path file) {
        return write(file, entries(
                "META-INF/MANIFEST.MF", text("Manifest-Version: 1.0\r\nMain-Class: net.minecraft.server.MinecraftServer\r\n\r\n"),
                "net/minecraft/data/Main.class", FetchFixtures.inventedBytes(5, 64),
                "net/minecraft/server/MinecraftServer.class", FetchFixtures.inventedBytes(6, 64)));
    }

    public static Path jarWithManifest(Path file, String manifest, boolean withDataMain) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/MANIFEST.MF", text(manifest));
        if (withDataMain) {
            entries.put("net/minecraft/data/Main.class", FetchFixtures.inventedBytes(5, 64));
        }
        return write(file, entries);
    }

    public static Path jarWithoutManifest(Path file) {
        return write(file, entries("net/minecraft/data/Main.class", FetchFixtures.inventedBytes(5, 64)));
    }

    public static Path write(Path file, Map<String, byte[]> entries) {
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return file;
    }

    public static byte[] text(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, byte[]> entries(Object... namesAndContents) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int index = 0; index < namesAndContents.length; index += 2) {
            entries.put((String) namesAndContents[index], (byte[]) namesAndContents[index + 1]);
        }
        return entries;
    }
}
