package tlm.dev.lectern.pipeline.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record Sha1(String hex) {

    private static final Pattern LOWERCASE_HEX = Pattern.compile("[0-9a-f]{40}");

    public Sha1 {
        Objects.requireNonNull(hex, "hex");
        if (!LOWERCASE_HEX.matcher(hex).matches()) {
            throw new IllegalArgumentException("A SHA-1 is 40 lowercase hexadecimal characters: '" + hex + "'");
        }
    }
}
