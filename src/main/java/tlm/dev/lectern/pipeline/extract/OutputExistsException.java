package tlm.dev.lectern.pipeline.extract;

import java.nio.file.Path;
import tlm.dev.lectern.pipeline.model.PipelineException;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class OutputExistsException extends PipelineException {

    public OutputExistsException(VersionId id, Path output) {
        super("Version " + id.value() + ": the folder " + output + " already exists. Nothing was changed, remove it yourself to extract again");
    }
}
