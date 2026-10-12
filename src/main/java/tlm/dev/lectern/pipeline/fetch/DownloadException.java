package tlm.dev.lectern.pipeline.fetch;

import tlm.dev.lectern.pipeline.model.PipelineException;

public final class DownloadException extends PipelineException {

    public DownloadException(String message) {
        super(message);
    }

    public DownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
