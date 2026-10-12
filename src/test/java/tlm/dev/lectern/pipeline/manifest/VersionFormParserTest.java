package tlm.dev.lectern.pipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tlm.dev.lectern.pipeline.model.VersionId;

class VersionFormParserTest {

    private static Optional<String> labelOf(String id) {
        return VersionFormParser.parse(new VersionId(id)).map(form -> form.kind().label());
    }

    static Stream<Arguments> oldNamingForms() {
        return Stream.of(
                Arguments.of("1.21.11", "release"),
                Arguments.of("1.21", "release"),
                Arguments.of("1.0", "release"),
                Arguments.of("1.7.10", "release"),
                Arguments.of("1.3", "release"),
                Arguments.of("1.21.11-rc3", "release-candidate"),
                Arguments.of("1.21-rc1", "release-candidate"),
                Arguments.of("1.21.11-pre5", "pre-release"),
                Arguments.of("1.21-pre1", "pre-release"),
                Arguments.of("25w46a", "snapshot"),
                Arguments.of("13w01a", "snapshot"),
                Arguments.of("17w13b", "snapshot"));
    }

    static Stream<Arguments> newNamingForms() {
        return Stream.of(
                Arguments.of("26.3", "release", new ReleaseLine(26, 3, 0)),
                Arguments.of("26.1", "release", new ReleaseLine(26, 1, 0)),
                Arguments.of("26.1.2", "release", new ReleaseLine(26, 1, 2)),
                Arguments.of("26.3-rc-3", "release-candidate", new ReleaseLine(26, 3, 0)),
                Arguments.of("26.1.1-rc-1", "release-candidate", new ReleaseLine(26, 1, 1)),
                Arguments.of("26.3-pre-3", "pre-release", new ReleaseLine(26, 3, 0)),
                Arguments.of("26.4-snapshot-3", "snapshot", new ReleaseLine(26, 4, 0)),
                Arguments.of("26.3-snapshot-10", "snapshot", new ReleaseLine(26, 3, 0)),
                Arguments.of("27.10.4-pre-12", "pre-release", new ReleaseLine(27, 10, 4)));
    }

    static Stream<String> idsOutsideTheFourForms() {
        return Stream.of(
                "1.14 Pre-Release 1",
                "1.14.2 Pre-Release 4",
                "1.RV-Pre1",
                "3D Shareware v1.34",
                "23w13a_or_b",
                "25w14craftmine",
                "24w14potato",
                "22w13oneblockatatime",
                "20w14infinite",
                "b1.8.1",
                "a1.2.6",
                "rd-132211",
                "c0.30_01c",
                "inf-20100618",
                "26.3-snapshot",
                "26.3-snapshot-",
                "26.3-snapshot-0",
                "26.3-rc3",
                "26.3-pre1",
                "1.21.11-rc-3",
                "1.21.11-pre-5",
                "1.21.11-snapshot-1",
                "26.3-RC-1",
                "26w14",
                "26w14A",
                "26w1a",
                "126w14a",
                "26.03",
                "26.3.",
                "26.3-rc-03",
                "6.3",
                "126.3",
                "1.",
                "1.21.",
                "1.21.11.1",
                "26.3.1.1",
                "26.3-rc-1-extra",
                "latest",
                "snapshot");
    }

    static Stream<Arguments> oldNamingLines() {
        return Stream.of(
                Arguments.of("1.21.11", new ReleaseLine(1, 21, 11)),
                Arguments.of("1.21", new ReleaseLine(1, 21, 0)),
                Arguments.of("1.0", new ReleaseLine(1, 0, 0)),
                Arguments.of("1.7.10", new ReleaseLine(1, 7, 10)),
                Arguments.of("1.21.11-rc3", new ReleaseLine(1, 21, 11)),
                Arguments.of("1.21-rc1", new ReleaseLine(1, 21, 0)),
                Arguments.of("1.20.5-pre4", new ReleaseLine(1, 20, 5)),
                Arguments.of("1.21-pre1", new ReleaseLine(1, 21, 0)));
    }

    @ParameterizedTest
    @MethodSource("oldNamingForms")
    void oldNamingIdGivesItsKind(String id, String label) {
        VersionForm form = VersionFormParser.parse(new VersionId(id)).orElseThrow();
        assertInstanceOf(VersionForm.OldNaming.class, form);
        assertEquals(label, form.kind().label());
    }

    @ParameterizedTest
    @MethodSource("oldNamingLines")
    void oldNamingReleaseCandidateAndPreReleaseCarryTheLineOfTheReleaseTheyLeadTo(String id, ReleaseLine line) {
        assertEquals(Optional.of(line), VersionFormParser.parse(new VersionId(id)).orElseThrow().releaseLine());
    }

    @ParameterizedTest
    @ValueSource(strings = {"25w46a", "13w01a", "17w13b", "24w14a"})
    void oldNamingSnapshotHasNoLineInItsId(String id) {
        VersionForm form = VersionFormParser.parse(new VersionId(id)).orElseThrow();
        assertInstanceOf(VersionForm.OldNaming.class, form);
        assertTrue(form.releaseLine().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("newNamingForms")
    void newNamingIdGivesItsKindAndItsReleaseLine(String id, String label, ReleaseLine line) {
        VersionForm form = VersionFormParser.parse(new VersionId(id)).orElseThrow();
        assertInstanceOf(VersionForm.NewNaming.class, form);
        assertEquals(label, form.kind().label());
        assertEquals(Optional.of(line), form.releaseLine());
    }

    @ParameterizedTest
    @MethodSource("idsOutsideTheFourForms")
    void idOutsideTheFourFormsGivesNoKind(String id) {
        assertEquals(Optional.empty(), labelOf(id));
    }
}
