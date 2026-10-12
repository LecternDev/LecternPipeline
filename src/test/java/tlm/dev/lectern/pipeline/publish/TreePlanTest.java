package tlm.dev.lectern.pipeline.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TreePlanTest {

    private final TreePlan plan = new TreePlan(Set.of("reports/a.json", "reports/b.json", "data/c.json"), Set.of("reports/b.json", "data/c.json", "data/old.json"));

    @Test
    void addedFilesAreIncomingAndNotTracked() {
        assertEquals(Set.of("reports/a.json"), plan.added());
    }

    @Test
    void removedFilesAreTrackedAndNotIncoming() {
        assertEquals(Set.of("data/old.json"), plan.removed());
    }

    @Test
    void keptFilesAreBothIncomingAndTracked() {
        assertEquals(Set.of("reports/b.json", "data/c.json"), plan.kept());
    }

    @Test
    void emptyTrackedTreeAddsEverything() {
        TreePlan first = new TreePlan(Set.of("reports/a.json"), Set.of());
        assertEquals(Set.of("reports/a.json"), first.added());
        assertEquals(Set.of(), first.removed());
        assertEquals(Set.of(), first.kept());
    }

    @Test
    void emptyIncomingTreeRemovesEverything() {
        TreePlan emptied = new TreePlan(Set.of(), Set.of("reports/a.json"));
        assertEquals(Set.of("reports/a.json"), emptied.removed());
        assertEquals(Set.of(), emptied.added());
    }
}
