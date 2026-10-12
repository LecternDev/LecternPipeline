package tlm.dev.lectern.pipeline;

import java.nio.file.Path;
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

    record Normalize(VersionId id) implements Command {

        public Normalize {
            Objects.requireNonNull(id, "id");
        }
    }

    record Publish(VersionId id, Path dataRepository) implements Command {

        public Publish {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(dataRepository, "dataRepository");
        }
    }
}
