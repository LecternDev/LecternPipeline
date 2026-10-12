package tlm.dev.lectern.pipeline;

import java.util.Objects;
import tlm.dev.lectern.pipeline.model.VersionId;

public sealed interface Command {

    record ListVersions() implements Command {
    }

    record Fetch(VersionId id) implements Command {

        public Fetch {
            Objects.requireNonNull(id, "id");
        }
    }

    record Extract(VersionId id) implements Command {

        public Extract {
            Objects.requireNonNull(id, "id");
        }
    }
}
