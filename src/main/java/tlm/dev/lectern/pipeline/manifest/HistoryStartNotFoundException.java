package tlm.dev.lectern.pipeline.manifest;

import tlm.dev.lectern.pipeline.model.PipelineException;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class HistoryStartNotFoundException extends PipelineException {

    public HistoryStartNotFoundException(VersionId id) {
        super("The first version of the history, '" + id.value() + "', is not among the versions of the Mojang manifest that are in scope,"
                + " so the history cannot be delimited. Give an existing version with --history-start");
    }
}
