package tlm.dev.lectern.pipeline.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private final DiffBasePlanner planner = new DiffBasePlanner(HISTORY);

    private DiffBase baseOf(String id) {
        TrackedVersion version = HISTORY.stream()
                .filter(candidate -> candidate.id().value().equals(id))
                .findFirst()
                .orElseThrow();
        return planner.baseOf(version);
    }

    private static Optional<VersionId> id(String value) {
        return Optional.of(new VersionId(value));
    }

    static Stream<Arguments> bases() {
        String none = "-";
        return Stream.of(
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

    @ParameterizedTest
    @MethodSource("bases")
    void stepAndReleaseBasesFollowTheRule(String version, String step, String release) {
        Optional<VersionId> expectedRelease = release.equals("-") ? Optional.empty() : id(release);
        assertEquals(new DiffBase(id(step), expectedRelease), baseOf(version), version);
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
    }

    @Test
    void oldNamingVersionsHaveNoBaseAtAll() {
        assertEquals(DiffBase.none(), baseOf("1.21.11"));
        assertEquals(DiffBase.none(), baseOf("25w46a"));
    }

    @Test
    void firstVersionOfTheListHasNoBaseWhenNothingPrecedesIt() {
        DiffBasePlanner alone = new DiffBasePlanner(List.of(ManifestFixtures.tracked("26.1-snapshot-1", 1)));
        assertEquals(DiffBase.none(), alone.baseOf(ManifestFixtures.tracked("26.1-snapshot-1", 1)));
    }

    @Test
    void baseNeverDependsOnVersionsPublishedAfterIt() {
        for (int cut = 1; cut <= HISTORY.size(); cut++) {
            DiffBasePlanner truncated = new DiffBasePlanner(HISTORY.subList(0, cut));
            TrackedVersion last = HISTORY.get(cut - 1);
            assertEquals(planner.baseOf(last), truncated.baseOf(last), last.id().value());
        }
    }

    @Test
    void baseDoesNotDependOnTheOrderOfTheInput() {
        for (long seed = 0; seed < 20; seed++) {
            List<TrackedVersion> shuffled = new ArrayList<>(HISTORY);
            Collections.shuffle(shuffled, new Random(seed));
            DiffBasePlanner shuffledPlanner = new DiffBasePlanner(shuffled);
            for (TrackedVersion version : HISTORY) {
                assertEquals(planner.baseOf(version), shuffledPlanner.baseOf(version), version.id().value() + " seed " + seed);
            }
        }
    }
}
