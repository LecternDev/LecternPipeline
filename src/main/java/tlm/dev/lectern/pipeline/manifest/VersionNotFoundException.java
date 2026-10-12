package tlm.dev.lectern.pipeline.manifest;

import tlm.dev.lectern.pipeline.model.PipelineException;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class VersionNotFoundException extends PipelineException {

    public VersionNotFoundException(VersionId id) {
        super("The version '" + id.value() + "' is not in the Mojang manifest");
    }
}
