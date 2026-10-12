package tlm.dev.lectern.pipeline.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VersionKindTest {

    static Stream<Arguments> kinds() {
        return Stream.of(
                Arguments.of(new VersionKind.Release(), "release"),
                Arguments.of(new VersionKind.ReleaseCandidate(), "release-candidate"),
                Arguments.of(new VersionKind.PreRelease(), "pre-release"),
                Arguments.of(new VersionKind.Snapshot(), "snapshot"));
    }

    @ParameterizedTest
    @MethodSource("kinds")
    void labelIsTheSharedContractLabel(VersionKind kind, String label) {
        assertEquals(label, kind.label());
    }
}
