package tlm.dev.lectern.pipeline.manifest;

import java.util.Objects;
import java.util.Optional;
import tlm.dev.lectern.pipeline.model.VersionKind;

public sealed interface VersionForm {

    VersionKind kind();

    Optional<ReleaseLine> releaseLine();

    record OldNaming(VersionKind kind, Optional<ReleaseLine> line) implements VersionForm {

        public OldNaming {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(line, "line");
        }

        @Override
        public Optional<ReleaseLine> releaseLine() {
            return line;
        }
    }

    record NewNaming(VersionKind kind, ReleaseLine line) implements VersionForm {

        public NewNaming {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(line, "line");
        }

        @Override
        public Optional<ReleaseLine> releaseLine() {
            return Optional.of(line);
        }
    }
}
