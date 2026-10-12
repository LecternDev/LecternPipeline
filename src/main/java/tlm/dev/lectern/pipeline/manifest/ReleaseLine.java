package tlm.dev.lectern.pipeline.manifest;

public record ReleaseLine(int year, int drop, int patch) {

    public ReleaseLine {
        if (year < 0 || drop < 0 || patch < 0) {
            throw new IllegalArgumentException("A release line has no negative part: " + year + "." + drop + "." + patch);
        }
    }

    public boolean isPatch() {
        return patch > 0;
    }

    public boolean sameDrop(ReleaseLine other) {
        return year == other.year && drop == other.drop;
    }
}
