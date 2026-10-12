package tlm.dev.lectern.pipeline;

import java.util.ArrayList;
import java.util.List;

final class SourceScanner {

    record ScannedSource(String code, List<Integer> commentLines) {
    }

    private enum State {
        CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, CHARACTER, TEXT_BLOCK
    }

    private static final String LINE_COMMENT_START = "//";
    private static final String BLOCK_COMMENT_START = "/*";
    private static final String BLOCK_COMMENT_END = "*/";
    private static final String TEXT_BLOCK_DELIMITER = "\"\"\"";

    private final String source;
    private final StringBuilder code = new StringBuilder();
    private final List<Integer> commentLines = new ArrayList<>();
    private int position;
    private int line = 1;
    private State state = State.CODE;

    private SourceScanner(String source) {
        this.source = source;
    }

    static ScannedSource scan(String source) {
        return new SourceScanner(source).run();
    }

    static List<Integer> nonAsciiLines(String source) {
        List<Integer> lines = new ArrayList<>();
        String[] all = source.split("\n", -1);
        for (int index = 0; index < all.length; index++) {
            if (all[index].chars().anyMatch(character -> character > 127)) {
                lines.add(index + 1);
            }
        }
        return lines;
    }

    private ScannedSource run() {
        while (position < source.length()) {
            switch (state) {
                case CODE -> scanCode();
                case LINE_COMMENT -> scanLineComment();
                case BLOCK_COMMENT -> scanBlockComment();
                case STRING -> scanQuoted('"');
                case CHARACTER -> scanQuoted('\'');
                case TEXT_BLOCK -> scanTextBlock();
            }
        }
        return new ScannedSource(code.toString(), List.copyOf(commentLines));
    }

    private void scanCode() {
        if (source.startsWith(LINE_COMMENT_START, position)) {
            commentLines.add(line);
            state = State.LINE_COMMENT;
            consume(LINE_COMMENT_START.length(), false);
        } else if (source.startsWith(BLOCK_COMMENT_START, position)) {
            commentLines.add(line);
            state = State.BLOCK_COMMENT;
            consume(BLOCK_COMMENT_START.length(), false);
        } else if (source.startsWith(TEXT_BLOCK_DELIMITER, position)) {
            state = State.TEXT_BLOCK;
            consume(TEXT_BLOCK_DELIMITER.length(), true);
        } else if (source.charAt(position) == '"') {
            state = State.STRING;
            consume(1, true);
        } else if (source.charAt(position) == '\'') {
            state = State.CHARACTER;
            consume(1, true);
        } else {
            consume(1, true);
        }
    }

    private void scanLineComment() {
        if (source.charAt(position) == '\n') {
            state = State.CODE;
        } else {
            consume(1, false);
        }
    }

    private void scanBlockComment() {
        if (source.startsWith(BLOCK_COMMENT_END, position)) {
            state = State.CODE;
            consume(BLOCK_COMMENT_END.length(), false);
        } else {
            consume(1, false);
        }
    }

    private void scanQuoted(char delimiter) {
        char current = source.charAt(position);
        if (current == '\\') {
            consume(2, false);
        } else if (current == delimiter || current == '\n') {
            state = State.CODE;
            consume(1, true);
        } else {
            consume(1, false);
        }
    }

    private void scanTextBlock() {
        if (source.charAt(position) == '\\') {
            consume(2, false);
        } else if (source.startsWith(TEXT_BLOCK_DELIMITER, position)) {
            state = State.CODE;
            consume(TEXT_BLOCK_DELIMITER.length(), true);
        } else {
            consume(1, false);
        }
    }

    private void consume(int count, boolean keep) {
        for (int consumed = 0; consumed < count && position < source.length(); consumed++) {
            char current = source.charAt(position++);
            if (current == '\n') {
                line++;
            }
            boolean lineBreak = current == '\n' || current == '\r';
            code.append(keep || lineBreak ? current : ' ');
        }
    }
}
