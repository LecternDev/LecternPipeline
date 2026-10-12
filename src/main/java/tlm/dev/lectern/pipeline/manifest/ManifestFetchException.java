package tlm.dev.lectern.pipeline.manifest;

import tlm.dev.lectern.pipeline.model.PipelineException;

public final class ManifestFetchException extends PipelineException {

    public ManifestFetchException(String message) {
        super(message);
    }

    public ManifestFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
