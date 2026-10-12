package tlm.dev.lectern.pipeline.fetch;

import java.nio.file.Path;
import tlm.dev.lectern.pipeline.model.PipelineException;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class IntegrityException extends PipelineException {

    private IntegrityException(String message) {
        super(message);
    }

    public static IntegrityException hash(VersionId id, String artifact, Sha1 expected, Sha1 received) {
        return new IntegrityException("Version " + id.value() + ": the " + artifact + " does not have the expected SHA-1. Expected "
                + expected.hex() + ", received " + received.hex());
    }

    public static IntegrityException size(VersionId id, String artifact, long expected, long received) {
        return new IntegrityException("Version " + id.value() + ": the " + artifact + " does not have the expected size. Expected "
                + expected + " bytes, received " + received + " bytes");
    }

    public static IntegrityException existingJar(VersionId id, Path jar, String detail) {
        return new IntegrityException("Version " + id.value() + ": the jar already at " + jar + " does not match the manifest. " + detail
                + ". It was left untouched, remove it yourself to download it again");
    }
}
