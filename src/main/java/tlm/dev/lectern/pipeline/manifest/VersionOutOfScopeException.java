package tlm.dev.lectern.pipeline.manifest;

import tlm.dev.lectern.pipeline.model.PipelineException;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class VersionOutOfScopeException extends PipelineException {

    private VersionOutOfScopeException(String message) {
        super(message);
    }

    public static VersionOutOfScopeException excluded(VersionId id) {
        return new VersionOutOfScopeException("The version '" + id.value() + "' is in the exclusion list (a joke version), it is not processed");
    }

    public static VersionOutOfScopeException unknownShape(VersionId id) {
        return new VersionOutOfScopeException("The version '" + id.value()
                + "' is not shaped like a release, a release candidate, a pre-release or a snapshot, it is not processed");
    }
}
