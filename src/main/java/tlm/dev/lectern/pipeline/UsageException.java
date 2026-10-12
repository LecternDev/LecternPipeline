package tlm.dev.lectern.pipeline;

import tlm.dev.lectern.pipeline.model.PipelineException;

public final class UsageException extends PipelineException {

    public UsageException(String message) {
        super(message);
    }
}
