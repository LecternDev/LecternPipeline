package tlm.dev.lectern.pipeline.extract;

import java.nio.file.Path;
import tlm.dev.lectern.pipeline.model.PipelineException;

public final class UnsupportedJarException extends PipelineException {

    public UnsupportedJarException(Path jar, String reason) {
        super("The jar " + jar + " is not a server jar the data generator can be run on: " + reason);
    }

    public UnsupportedJarException(Path jar, String reason, Throwable cause) {
        super("The jar " + jar + " is not a server jar the data generator can be run on: " + reason, cause);
    }
}
