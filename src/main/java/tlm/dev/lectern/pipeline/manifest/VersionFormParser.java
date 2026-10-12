package tlm.dev.lectern.pipeline.manifest;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tlm.dev.lectern.pipeline.model.VersionId;
import tlm.dev.lectern.pipeline.model.VersionKind;

public final class VersionFormParser {

    private static final String NUMBER = "(0|[1-9][0-9]{0,3})";
    private static final String COUNTER = "[1-9][0-9]{0,3}";
    private static final String OLD_RELEASE = "1\\.(0|[1-9][0-9]{0,3})(?:\\.(0|[1-9][0-9]{0,3}))?";
    private static final int OLD_MAJOR = 1;
    private static final String NEW_RELEASE = "([0-9]{2})\\." + NUMBER + "(?:\\." + NUMBER + ")?";

    private record Form(Pattern pattern, VersionKind kind, boolean carriesLine) {

        static Form of(String regex, VersionKind kind) {
            return new Form(Pattern.compile(regex), kind, true);
        }

        static Form withoutLine(String regex, VersionKind kind) {
            return new Form(Pattern.compile(regex), kind, false);
        }
    }

    private static final List<Form> OLD_FORMS = List.of(
            Form.of(OLD_RELEASE, new VersionKind.Release()),
            Form.of(OLD_RELEASE + "-rc" + COUNTER, new VersionKind.ReleaseCandidate()),
            Form.of(OLD_RELEASE + "-pre" + COUNTER, new VersionKind.PreRelease()),
            Form.withoutLine("[0-9]{2}w[0-9]{2}[a-z]", new VersionKind.Snapshot()));

    private static final List<Form> NEW_FORMS = List.of(
            Form.of(NEW_RELEASE, new VersionKind.Release()),
            Form.of(NEW_RELEASE + "-rc-" + COUNTER, new VersionKind.ReleaseCandidate()),
            Form.of(NEW_RELEASE + "-pre-" + COUNTER, new VersionKind.PreRelease()),
            Form.of(NEW_RELEASE + "-snapshot-" + COUNTER, new VersionKind.Snapshot()));

    private VersionFormParser() {
    }

    public static Optional<VersionForm> parse(VersionId id) {
        String value = id.value();
        return parseNewNaming(value).or(() -> parseOldNaming(value));
    }

    private static Optional<VersionForm> parseNewNaming(String value) {
        for (Form form : NEW_FORMS) {
            Matcher matcher = form.pattern().matcher(value);
            if (matcher.matches()) {
                return Optional.of(new VersionForm.NewNaming(form.kind(), lineOf(matcher)));
            }
        }
        return Optional.empty();
    }

    private static Optional<VersionForm> parseOldNaming(String value) {
        for (Form form : OLD_FORMS) {
            Matcher matcher = form.pattern().matcher(value);
            if (matcher.matches()) {
                return Optional.of(new VersionForm.OldNaming(form.kind(), form.carriesLine() ? Optional.of(oldLineOf(matcher)) : Optional.empty()));
            }
        }
        return Optional.empty();
    }

    private static ReleaseLine oldLineOf(Matcher matcher) {
        String patch = matcher.group(2);
        return new ReleaseLine(OLD_MAJOR, Integer.parseInt(matcher.group(1)), patch == null ? 0 : Integer.parseInt(patch));
    }

    private static ReleaseLine lineOf(Matcher matcher) {
        String patch = matcher.group(3);
        return new ReleaseLine(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                patch == null ? 0 : Integer.parseInt(patch));
    }
}
