package tlm.dev.lectern.pipeline.publish;

import tlm.dev.lectern.pipeline.model.PipelineException;

public final class PublicationException extends PipelineException {

    public PublicationException(String message) {
        super(message);
    }

    public PublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
