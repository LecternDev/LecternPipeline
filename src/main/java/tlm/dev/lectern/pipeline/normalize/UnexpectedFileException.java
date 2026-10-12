package tlm.dev.lectern.pipeline.normalize;

import tlm.dev.lectern.pipeline.model.PipelineException;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class UnexpectedFileException extends PipelineException {

    public UnexpectedFileException(VersionId id, String file, String reason) {
        super("Version " + id.value() + ": the file '" + file + "' is not on the allowlist: " + reason
                + ". Only .json and .mcmeta files under reports/ and data/ are kept, a new kind of file has to be decided by a person."
                + " Nothing was written");
    }
}
