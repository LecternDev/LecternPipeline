package tlm.dev.lectern.pipeline.publish;

import tlm.dev.lectern.pipeline.model.PipelineException;

public final class GitException extends PipelineException {

    public GitException(String message) {
        super(message);
    }

    public GitException(String message, Throwable cause) {
        super(message, cause);
    }
}
