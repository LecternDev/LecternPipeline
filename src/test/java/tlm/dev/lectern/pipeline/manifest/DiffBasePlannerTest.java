package tlm.dev.lectern.pipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tlm.dev.lectern.pipeline.fixtures.ManifestFixtures;
import tlm.dev.lectern.pipeline.model.VersionId;

class DiffBasePlannerTest {

    private static final List<TrackedVersion> HISTORY = List.of(
            ManifestFixtures.tracked("25w46a", 0),
            ManifestFixtures.tracked("1.21.11", 24),
            ManifestFixtures.tracked("26.1-snapshot-1", 48),
            ManifestFixtures.tracked("26.1-snapshot-2", 72),
            ManifestFixtures.tracked("26.1-pre-1", 96),
            ManifestFixtures.tracked("26.1-rc-1", 120),
            ManifestFixtures.tracked("26.1", 144),
            ManifestFixtures.tracked("26.1.1-rc-1", 168),
            ManifestFixtures.tracked("26.1.1", 192),
            ManifestFixtures.tracked("26.2-snapshot-1", 216),
            ManifestFixtures.tracked("26.1.2-rc-1", 240),
            ManifestFixtures.tracked("26.1.2", 264),
            ManifestFixtures.tracked("26.2-snapshot-2", 266),
            ManifestFixtures.tracked("26.2-pre-1", 288),
            ManifestFixtures.tracked("26.2-rc-1", 312),
            ManifestFixtures.tracked("26.2", 336),
            ManifestFixtures.tracked("26.3-snapshot-1", 360),
            ManifestFixtures.tracked("26.1.3-rc-1", 384),
            ManifestFixtures.tracked("26.1.3", 408));

    private static final List<TrackedVersion> OLD_HISTORY = List.of(
            ManifestFixtures.tracked("1.20.4", 0),
            ManifestFixtures.tracked("23w51a", 24),
            ManifestFixtures.tracked("23w51b", 26),
            ManifestFixtures.tracked("1.20.5-pre1", 72),
            ManifestFixtures.tracked("1.20.5-pre2", 96),
            ManifestFixtures.tracked("1.20.5-rc1", 120),
            ManifestFixtures.tracked("1.20.5", 144),
            ManifestFixtures.tracked("1.20.6-rc1", 168),
            ManifestFixtures.tracked("1.20.6", 192),
            ManifestFixtures.tracked("24w18a", 216),
            ManifestFixtures.tracked("24w19a", 240),
            ManifestFixtures.tracked("24w19b", 242),
            ManifestFixtures.tracked("1.21-pre1", 264),
            ManifestFixtures.tracked("1.21-rc1", 288),
            ManifestFixtures.tracked("1.21", 312),
            ManifestFixtures.tracked("1.21.1-rc1", 336),
            ManifestFixtures.tracked("1.21.1", 360),
            ManifestFixtures.tracked("24w33a", 384),
            ManifestFixtures.tracked("24w34a", 408),
            ManifestFixtures.tracked("1.21.3", 432),
            ManifestFixtures.tracked("24w44a", 456));

    private final DiffBasePlanner planner = new DiffBasePlanner(HISTORY);
    private final DiffBasePlanner oldPlanner = new DiffBasePlanner(OLD_HISTORY);

    private static DiffBase baseOf(DiffBasePlanner planner, List<TrackedVersion> versions, String id) {
        TrackedVersion version = versions.stream()
                .filter(candidate -> candidate.id().value().equals(id))
                .findFirst()
                .orElseThrow();
        return planner.baseOf(version);
    }

    private DiffBase baseOf(String id) {
        return baseOf(planner, HISTORY, id);
    }

    private DiffBase oldBaseOf(String id) {
        return baseOf(oldPlanner, OLD_HISTORY, id);
    }

    private static Optional<VersionId> id(String value) {
        return Optional.of(new VersionId(value));
    }

    private static Optional<VersionId> idOrNone(String value) {
        return value.equals("-") ? Optional.empty() : id(value);
    }

    static Stream<Arguments> bases() {
        String none = "-";
        return Stream.of(
                Arguments.of("25w46a", none, none),
                Arguments.of("1.21.11", "25w46a", none),
                Arguments.of("26.1-snapshot-1", "1.21.11", none),
                Arguments.of("26.1-snapshot-2", "26.1-snapshot-1", none),
                Arguments.of("26.1-pre-1", "26.1-snapshot-2", none),
                Arguments.of("26.1-rc-1", "26.1-pre-1", none),
                Arguments.of("26.1", "26.1-rc-1", "1.21.11"),
                Arguments.of("26.1.1-rc-1", "26.1", none),
                Arguments.of("26.1.1", "26.1.1-rc-1", "26.1"),
                Arguments.of("26.2-snapshot-1", "26.1.1", none),
                Arguments.of("26.1.2-rc-1", "26.1.1", none),
                Arguments.of("26.1.2", "26.1.2-rc-1", "26.1.1"),
                Arguments.of("26.2-snapshot-2", "26.2-snapshot-1", none),
                Arguments.of("26.2-pre-1", "26.2-snapshot-2", none),
                Arguments.of("26.2-rc-1", "26.2-pre-1", none),
                Arguments.of("26.2", "26.2-rc-1", "26.1.2"),
                Arguments.of("26.3-snapshot-1", "26.2", none),
                Arguments.of("26.1.3-rc-1", "26.1.2", none),
                Arguments.of("26.1.3", "26.1.3-rc-1", "26.1.2"));
    }

    static Stream<Arguments> oldBases() {
        String none = "-";
        return Stream.of(
                Arguments.of("1.20.4", none, none),
                Arguments.of("23w51a", "1.20.4", none),
                Arguments.of("23w51b", "23w51a", none),
                Arguments.of("1.20.5-pre1", "23w51b", none),
                Arguments.of("1.20.5-pre2", "1.20.5-pre1", none),
                Arguments.of("1.20.5-rc1", "1.20.5-pre2", none),
                Arguments.of("1.20.5", "1.20.5-rc1", "1.20.4"),
                Arguments.of("1.20.6-rc1", "1.20.5", none),
                Arguments.of("1.20.6", "1.20.6-rc1", "1.20.5"),
                Arguments.of("24w18a", "1.20.6", none),
                Arguments.of("24w19a", "24w18a", none),
                Arguments.of("24w19b", "24w19a", none),
                Arguments.of("1.21-pre1", "24w19b", none),
                Arguments.of("1.21-rc1", "1.21-pre1", none),
                Arguments.of("1.21", "1.21-rc1", "1.20.6"),
                Arguments.of("1.21.1-rc1", "1.21", none),
                Arguments.of("1.21.1", "1.21.1-rc1", "1.21"),
                Arguments.of("24w33a", "1.21.1", none),
                Arguments.of("24w34a", "24w33a", none),
                Arguments.of("1.21.3", "24w34a", "1.21.1"),
                Arguments.of("24w44a", "1.21.3", none));
    }

    @ParameterizedTest
    @MethodSource("bases")
    void stepAndReleaseBasesFollowTheRuleOfTheNewNaming(String version, String step, String release) {
        assertEquals(new DiffBase(idOrNone(step), idOrNone(release)), baseOf(version), version);
    }

    @ParameterizedTest
    @MethodSource("oldBases")
    void stepAndReleaseBasesFollowTheRuleOfTheOldNaming(String version, String step, String release) {
        assertEquals(new DiffBase(idOrNone(step), idOrNone(release)), oldBaseOf(version), version);
    }

    @Test
    void snapshotBetweenTwoPatchReleasesIsComparedWithThePatchReleaseBeforeIt() {
        assertEquals(id("26.1.1"), baseOf("26.2-snapshot-1").step());
    }

    @Test
    void secondSnapshotOfADropIsComparedWithItsPredecessorEvenWhenAReleaseSitsBetweenByDate() {
        assertEquals(id("26.2-snapshot-1"), baseOf("26.2-snapshot-2").step());
    }

    @Test
    void patchReleaseCandidateIsComparedWithTheReleaseBeforeItInTheSameDrop() {
        assertEquals(id("26.1.1"), baseOf("26.1.2-rc-1").step());
    }

    @Test
    void lateHotfixIsComparedWithItsOwnDropAndNotWithTheNewerDropReleasedBefore() {
        assertEquals(id("26.1.2"), baseOf("26.1.3-rc-1").step());
        assertEquals(id("26.1.2"), baseOf("26.1.3").release());
    }

    @Test
    void firstVersionOfANewNamingDropIsComparedWithTheLastOldNamingRelease() {
        assertEquals(id("1.21.11"), baseOf("26.1-snapshot-1").step());
        assertEquals(id("1.21.11"), baseOf("26.1").release());
    }

    @Test
    void nonReleaseVersionsHaveNoReleaseBase() {
        for (String version : List.of("26.1-snapshot-1", "26.1-pre-1", "26.1-rc-1", "26.1.1-rc-1", "26.2-snapshot-2")) {
            assertEquals(Optional.empty(), baseOf(version).release(), version);
        }
        for (String version : List.of("23w51a", "1.20.5-pre1", "1.20.5-rc1", "1.20.6-rc1", "24w19b", "1.21-pre1")) {
            assertEquals(Optional.empty(), oldBaseOf(version).release(), version);
        }
    }

    @Test
    void firstSnapshotAfterAReleaseIsComparedWithThatRelease() {
        assertEquals(id("1.20.6"), oldBaseOf("24w18a").step());
        assertEquals(id("1.21.1"), oldBaseOf("24w33a").step());
    }

    @Test
    void firstPreReleaseOfAnOldNamingLineTakesOverTheSnapshotsPublishedSinceTheLastRelease() {
        assertEquals(id("23w51b"), oldBaseOf("1.20.5-pre1").step());
        assertEquals(id("24w19b"), oldBaseOf("1.21-pre1").step());
    }

    @Test
    void hotfixWithoutSnapshotsSinceTheLastReleaseIsComparedWithThatRelease() {
        assertEquals(id("1.20.5"), oldBaseOf("1.20.6-rc1").step());
        assertEquals(id("1.21"), oldBaseOf("1.21.1-rc1").step());
    }

    @Test
    void releaseWithoutCandidateTakesOverTheOpenSnapshots() {
        assertEquals(id("24w34a"), oldBaseOf("1.21.3").step());
        assertEquals(id("1.21.1"), oldBaseOf("1.21.3").release());
    }

    @Test
    void snapshotKeepsTheSnapshotChainWhateverTheLinesOfTheVersionsAroundIt() {
        assertEquals(id("24w19a"), oldBaseOf("24w19b").step());
        assertEquals(id("1.21.3"), oldBaseOf("24w44a").step());
    }

    @Test
    void releaseBaseOfAnOldNamingReleaseIsThePreviousReleaseNotThePreviousVersion() {
        assertEquals(id("1.20.6"), oldBaseOf("1.21").release());
        assertEquals(id("1.21"), oldBaseOf("1.21.1").release());
        assertEquals(id("1.20.5"), oldBaseOf("1.20.6").release());
    }

    @Test
    void firstVersionOfTheListHasNoBaseWhenNothingPrecedesIt() {
        DiffBasePlanner alone = new DiffBasePlanner(List.of(ManifestFixtures.tracked("26.1-snapshot-1", 1)));
        assertEquals(DiffBase.none(), alone.baseOf(ManifestFixtures.tracked("26.1-snapshot-1", 1)));
        DiffBasePlanner oldAlone = new DiffBasePlanner(List.of(ManifestFixtures.tracked("1.20.4", 1)));
        assertEquals(DiffBase.none(), oldAlone.baseOf(ManifestFixtures.tracked("1.20.4", 1)));
    }

    @Test
    void baseNeverDependsOnVersionsPublishedAfterIt() {
        for (List<TrackedVersion> versions : List.of(HISTORY, OLD_HISTORY)) {
            DiffBasePlanner whole = new DiffBasePlanner(versions);
            for (int cut = 1; cut <= versions.size(); cut++) {
                DiffBasePlanner truncated = new DiffBasePlanner(versions.subList(0, cut));
                TrackedVersion last = versions.get(cut - 1);
                assertEquals(whole.baseOf(last), truncated.baseOf(last), last.id().value());
            }
        }
    }

    @Test
    void baseDoesNotDependOnTheOrderOfTheInput() {
        for (List<TrackedVersion> versions : List.of(HISTORY, OLD_HISTORY)) {
            DiffBasePlanner reference = new DiffBasePlanner(versions);
            for (long seed = 0; seed < 20; seed++) {
                List<TrackedVersion> shuffled = new ArrayList<>(versions);
                Collections.shuffle(shuffled, new Random(seed));
                DiffBasePlanner shuffledPlanner = new DiffBasePlanner(shuffled);
                for (TrackedVersion version : versions) {
                    assertEquals(reference.baseOf(version), shuffledPlanner.baseOf(version), version.id().value() + " seed " + seed);
                }
            }
        }
    }

    @Test
    void baseThatFallsBeforeTheStartOfTheHistoryIsNone() {
        DiffBasePlanner fromTheCandidate = new DiffBasePlanner(OLD_HISTORY, id("1.20.5-rc1"));
        assertEquals(DiffBase.none(), baseOf(fromTheCandidate, OLD_HISTORY, "1.20.5-rc1"));
        assertEquals(new DiffBase(id("1.20.5-rc1"), Optional.empty()), baseOf(fromTheCandidate, OLD_HISTORY, "1.20.5"));
        assertEquals(id("1.20.5"), baseOf(fromTheCandidate, OLD_HISTORY, "1.20.6-rc1").step());
    }

    @Test
    void firstVersionOfTheHistoryHasNoBaseAndTheSecondOneHasTheFirstAsBase() {
        DiffBasePlanner fromTheStart = new DiffBasePlanner(OLD_HISTORY, id("1.20.4"));
        assertEquals(DiffBase.none(), baseOf(fromTheStart, OLD_HISTORY, "1.20.4"));
        assertEquals(id("1.20.4"), baseOf(fromTheStart, OLD_HISTORY, "23w51a").step());
    }

    @Test
    void historyStartInTheMiddleOfAnOldNamingLineCutsTheBasesBelowIt() {
        DiffBasePlanner fromTheSnapshot = new DiffBasePlanner(OLD_HISTORY, id("23w51b"));
        assertEquals(Optional.empty(), baseOf(fromTheSnapshot, OLD_HISTORY, "23w51b").step());
        assertEquals(id("23w51b"), baseOf(fromTheSnapshot, OLD_HISTORY, "1.20.5-pre1").step());
        assertEquals(Optional.empty(), baseOf(fromTheSnapshot, OLD_HISTORY, "1.20.5").release());
    }

    @Test
    void versionsBeforeTheStartAreOutOfTheHistory() {
        DiffBasePlanner fromTheCandidate = new DiffBasePlanner(OLD_HISTORY, id("1.20.5-rc1"));
        assertFalse(fromTheCandidate.inHistory(OLD_HISTORY.stream().filter(v -> v.id().value().equals("1.20.5-pre2")).findFirst().orElseThrow()));
        assertTrue(fromTheCandidate.inHistory(OLD_HISTORY.stream().filter(v -> v.id().value().equals("1.20.5-rc1")).findFirst().orElseThrow()));
        assertTrue(fromTheCandidate.inHistory(OLD_HISTORY.get(OLD_HISTORY.size() - 1)));
    }

    @Test
    void withoutAStartEveryVersionIsInTheHistory() {
        assertTrue(oldPlanner.inHistory(OLD_HISTORY.get(0)));
    }

    @Test
    void startThatIsNotAmongTheVersionsIsAnError() {
        HistoryStartNotFoundException failure = assertThrows(HistoryStartNotFoundException.class, () -> new DiffBasePlanner(OLD_HISTORY, id("1.19.4")));
        assertTrue(failure.getMessage().contains("'1.19.4'"), failure.getMessage());
        assertTrue(failure.getMessage().contains("--history-start"), failure.getMessage());
    }

    @Test
    void startMayBeAVersionOfTheNewNaming() {
        DiffBasePlanner fromNewNaming = new DiffBasePlanner(HISTORY, id("26.1-snapshot-1"));
        assertEquals(Optional.empty(), baseOf(fromNewNaming, HISTORY, "26.1-snapshot-1").step());
        assertEquals(Optional.empty(), baseOf(fromNewNaming, HISTORY, "26.1").release());
        assertEquals(id("26.1.1"), baseOf(fromNewNaming, HISTORY, "26.2-snapshot-1").step());
    }
}
