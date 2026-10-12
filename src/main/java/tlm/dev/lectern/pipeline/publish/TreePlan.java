package tlm.dev.lectern.pipeline.publish;

import java.util.HashSet;
import java.util.Set;

public record TreePlan(Set<String> incoming, Set<String> tracked) {

    public TreePlan {
        incoming = Set.copyOf(incoming);
        tracked = Set.copyOf(tracked);
    }

    public Set<String> added() {
        Set<String> added = new HashSet<>(incoming);
        added.removeAll(tracked);
        return Set.copyOf(added);
    }

    public Set<String> removed() {
        Set<String> removed = new HashSet<>(tracked);
        removed.removeAll(incoming);
        return Set.copyOf(removed);
    }

    public Set<String> kept() {
        Set<String> kept = new HashSet<>(incoming);
        kept.retainAll(tracked);
        return Set.copyOf(kept);
    }
}
