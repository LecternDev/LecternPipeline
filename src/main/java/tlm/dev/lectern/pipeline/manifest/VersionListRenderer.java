package tlm.dev.lectern.pipeline.manifest;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tlm.dev.lectern.pipeline.model.VersionId;

public final class VersionListRenderer {

    private static final List<String> HEADER = List.of("ID", "KIND", "RELEASED", "BASE", "RELEASE BASE");
    private static final String ABSENT = "-";
    private static final String GAP = "  ";
    private static final DateTimeFormatter RELEASE_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    public String render(List<ListedVersion> versions) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(HEADER);
        for (ListedVersion listed : versions) {
            TrackedVersion version = listed.version();
            rows.add(List.of(
                    version.id().value(),
                    version.kind().label(),
                    RELEASE_TIME.format(version.releaseTime()),
                    text(listed.base().step()),
                    text(listed.base().release())));
        }
        int[] widths = widths(rows);
        StringBuilder out = new StringBuilder();
        for (List<String> row : rows) {
            out.append(line(row, widths)).append('\n');
        }
        return out.toString();
    }

    private static String text(Optional<VersionId> id) {
        return id.map(VersionId::value).orElse(ABSENT);
    }

    private static int[] widths(List<List<String>> rows) {
        int[] widths = new int[HEADER.size()];
        for (List<String> row : rows) {
            for (int column = 0; column < widths.length; column++) {
                widths[column] = Math.max(widths[column], row.get(column).length());
            }
        }
        return widths;
    }

    private static String line(List<String> row, int[] widths) {
        StringBuilder line = new StringBuilder();
        for (int column = 0; column < widths.length; column++) {
            boolean last = column == widths.length - 1;
            line.append(last ? row.get(column) : pad(row.get(column), widths[column]) + GAP);
        }
        return line.toString();
    }

    private static String pad(String value, int width) {
        return value + " ".repeat(width - value.length());
    }
}
