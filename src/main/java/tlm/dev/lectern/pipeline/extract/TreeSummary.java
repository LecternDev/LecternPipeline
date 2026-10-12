package tlm.dev.lectern.pipeline.extract;

public record TreeSummary(long files, long bytes, long filesWithoutCache, long bytesWithoutCache) {

    public TreeSummary {
        if (files < 0 || bytes < 0 || filesWithoutCache < 0 || bytesWithoutCache < 0) {
            throw new IllegalArgumentException("A tree summary has no negative count");
        }
    }
}
