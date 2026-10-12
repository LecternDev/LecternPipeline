package tlm.dev.lectern.pipeline.publish;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record StagedSet(Map<String, String> statuses) {

    public StagedSet {
        statuses = Map.copyOf(statuses);
    }

    public static StagedSet parse(String nameStatusOutput) {
        Objects.requireNonNull(nameStatusOutput, "nameStatusOutput");
        String[] tokens = nameStatusOutput.split("\0");
        Map<String, String> statuses = new HashMap<>();
        int index = 0;
        while (index < tokens.length) {
            String status = tokens[index];
            if (status.isEmpty()) {
                index++;
                continue;
            }
            if (index + 1 >= tokens.length) {
                throw new PublicationException("Could not read the list of changed files from git: the status '" + status + "' has no path");
            }
            statuses.put(tokens[index + 1], status);
            index += 2;
        }
        return new StagedSet(statuses);
    }

    public int count(String status, String prefix) {
        return (int) statuses.entrySet().stream()
                .filter(entry -> entry.getValue().equals(status) && entry.getKey().startsWith(prefix))
                .count();
    }
}
