package tlm.dev.lectern.pipeline.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @MethodSource("kinds")
    void labelReadsBackToTheSameKind(VersionKind kind, String label) {
        assertEquals(Optional.of(kind), VersionKind.ofLabel(label));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Release", "stable", "release-candidate ", "pre_release", "old_beta"})
    void anythingButTheFourLabelsIsNotAKind(String label) {
        assertEquals(Optional.empty(), VersionKind.ofLabel(label));
    }
}
