package tlm.dev.lectern.pipeline.fetch;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import tlm.dev.lectern.pipeline.model.Sha1;

public final class Sha1Hasher {

    private static final String ALGORITHM = "SHA-1";
    private static final int BUFFER_SIZE = 64 * 1024;

    public Sha1 hash(byte[] data) {
        return new Sha1(HexFormat.of().formatHex(newDigest().digest(data)));
    }

    public Sha1 hash(Path file) {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream stream = Files.newInputStream(file)) {
            for (int read = stream.read(buffer); read >= 0; read = stream.read(buffer)) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file + " to compute its SHA-1", exception);
        }
        return new Sha1(HexFormat.of().formatHex(digest.digest()));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The JDK has no " + ALGORITHM + " digest", exception);
        }
    }
}
