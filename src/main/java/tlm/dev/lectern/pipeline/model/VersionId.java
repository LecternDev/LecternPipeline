package tlm.dev.lectern.pipeline.model;

import java.util.Objects;

public record VersionId(String value) {

    public VersionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException("A version id must not be blank or surrounded by whitespace: '" + value + "'");
        }
        if (value.chars().anyMatch(VersionId::isForbidden)) {
            throw new IllegalArgumentException("A version id must not contain a path separator or a control character: '" + value + "'");
        }
        if (value.equals(".") || value.equals("..")) {
            throw new IllegalArgumentException("A version id must not be a relative folder name: '" + value + "'");
        }
    }

    private static boolean isForbidden(int character) {
        return character == '/' || character == '\\' || Character.isISOControl(character);
    }
}
