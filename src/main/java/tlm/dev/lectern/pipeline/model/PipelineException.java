package tlm.dev.lectern.pipeline.model;

public abstract class PipelineException extends RuntimeException {

    protected PipelineException(String message) {
        super(message);
    }

    protected PipelineException(String message, Throwable cause) {
        super(message, cause);
    }
}
