package tlm.dev.lectern.pipeline.extract;

import tlm.dev.lectern.pipeline.model.PipelineException;

public final class ProcessLaunchException extends PipelineException {

    public ProcessLaunchException(String message, Throwable cause) {
        super(message, cause);
    }
}
