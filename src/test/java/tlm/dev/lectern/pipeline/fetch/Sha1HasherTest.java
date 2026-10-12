package tlm.dev.lectern.pipeline.fetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlm.dev.lectern.pipeline.fixtures.FetchFixtures;

class Sha1HasherTest {

    private final Sha1Hasher hasher = new Sha1Hasher();

    @Test
    void knownVectorsAreHashedAsPublished() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", hasher.hash("abc".getBytes(StandardCharsets.US_ASCII)).hex());
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", hasher.hash(new byte[0]).hex());
    }

    @Test
    void fileHashEqualsTheHashOfItsBytesEvenAcrossSeveralBuffers(@TempDir Path directory) throws IOException {
        byte[] bytes = FetchFixtures.inventedBytes(3, 300_000);
        Path file = directory.resolve("data.bin");
        Files.write(file, bytes);
        assertEquals(hasher.hash(bytes), hasher.hash(file));
        assertEquals(FetchFixtures.sha1Hex(bytes), hasher.hash(file).hex());
    }

    @Test
    void missingFileIsAnErrorNamingIt(@TempDir Path directory) {
        Path missing = directory.resolve("absent.bin");
        UncheckedIOException failure = assertThrows(UncheckedIOException.class, () -> hasher.hash(missing));
        assertEquals(true, failure.getMessage().contains("absent.bin"));
    }
}
