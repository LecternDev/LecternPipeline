package tlm.dev.lectern.pipeline;

import java.nio.file.Path;
import tlm.dev.lectern.pipeline.model.PipelineException;

public final class WorkbenchMissingException extends PipelineException {

    public WorkbenchMissingException(Path workbench) {
        super("The Workbench folder " + workbench + " does not exist. Run from the LecternPipeline folder, next to LecternWorkbench,"
                + " or pass --workbench=<folder>");
    }
}
