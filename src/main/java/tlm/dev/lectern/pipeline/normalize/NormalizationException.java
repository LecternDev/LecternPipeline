package tlm.dev.lectern.pipeline.normalize;

import tlm.dev.lectern.pipeline.model.PipelineException;

public final class NormalizationException extends PipelineException {

    public NormalizationException(String message) {
        super(message);
    }

    public NormalizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
