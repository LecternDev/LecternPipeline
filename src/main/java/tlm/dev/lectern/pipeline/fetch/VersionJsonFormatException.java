package tlm.dev.lectern.pipeline.fetch;

import tlm.dev.lectern.pipeline.model.PipelineException;

public final class VersionJsonFormatException extends PipelineException {

    public VersionJsonFormatException(String message) {
        super(message);
    }

    public VersionJsonFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
