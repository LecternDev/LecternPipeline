package tlm.dev.lectern.pipeline.diff;

import tlm.dev.lectern.pipeline.model.PipelineException;

public final class DiffException extends PipelineException {

    public DiffException(String message) {
        super(message);
    }

    public DiffException(String message, Throwable cause) {
        super(message, cause);
    }
}
