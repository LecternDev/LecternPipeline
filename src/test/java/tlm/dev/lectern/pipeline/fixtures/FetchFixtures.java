package tlm.dev.lectern.pipeline.fixtures;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Random;

public final class FetchFixtures {

    private FetchFixtures() {
    }

    public static byte[] inventedBytes(long seed, int length) {
        byte[] bytes = new byte[length];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    public static String sha1Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static byte[] versionJson(String id, String jarSha1, long jarSize, String jarUrl) {
        String text = "{\"id\": \"" + id + "\", \"type\": \"snapshot\", \"downloads\": {"
                + "\"client\": {\"sha1\": \"" + "0".repeat(40) + "\", \"size\": 10, \"url\": \"https://downloads.example.invalid/client.jar\"}, "
                + "\"server\": {\"sha1\": \"" + jarSha1 + "\", \"size\": " + jarSize + ", \"url\": \"" + jarUrl + "\"}}}";
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
