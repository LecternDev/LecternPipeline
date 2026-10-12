package tlm.dev.lectern.pipeline.json;

public final class JsonFormatException extends RuntimeException {

    public JsonFormatException(String message) {
        super(message);
    }

    public JsonFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
