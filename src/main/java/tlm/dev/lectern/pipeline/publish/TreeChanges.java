package tlm.dev.lectern.pipeline.publish;

public record TreeChanges(int added, int modified, int removed) {

    public TreeChanges {
        if (added < 0 || modified < 0 || removed < 0) {
            throw new IllegalArgumentException("A change count must not be negative");
        }
    }
}
