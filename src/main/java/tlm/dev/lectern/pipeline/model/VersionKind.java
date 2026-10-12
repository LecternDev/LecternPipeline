package tlm.dev.lectern.pipeline.model;

import java.util.Optional;
import java.util.stream.Stream;

public sealed interface VersionKind {

    String label();

    static Optional<VersionKind> ofLabel(String label) {
        return Stream.<VersionKind>of(new Release(), new ReleaseCandidate(), new PreRelease(), new Snapshot())
                .filter(kind -> kind.label().equals(label))
                .findFirst();
    }

    record Release() implements VersionKind {

        @Override
        public String label() {
            return "release";
        }
    }

    record ReleaseCandidate() implements VersionKind {

        @Override
        public String label() {
            return "release-candidate";
        }
    }

    record PreRelease() implements VersionKind {

        @Override
        public String label() {
            return "pre-release";
        }
    }

    record Snapshot() implements VersionKind {

        @Override
        public String label() {
            return "snapshot";
        }
    }
}
