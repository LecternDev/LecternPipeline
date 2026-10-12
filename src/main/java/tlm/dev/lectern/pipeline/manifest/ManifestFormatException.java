package tlm.dev.lectern.pipeline.manifest;

import tlm.dev.lectern.pipeline.model.PipelineException;

public final class ManifestFormatException extends PipelineException {

    public ManifestFormatException(String message) {
        super(message);
    }

    public ManifestFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
