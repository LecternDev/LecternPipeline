package tlm.dev.lectern.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class SourceScannerTest {

    private static List<Integer> commentLines(String source) {
        return SourceScanner.scan(source).commentLines();
    }

    @Test
    void lineCommentIsReportedWithItsLine() {
        String source = """
                class A {
                    int value = 1; // note
                }
                """;
        assertEquals(List.of(2), commentLines(source));
    }

    @Test
    void blockCommentIsReportedOnceAtItsFirstLine() {
        String source = """
                class A {
                    /* first
                       second */
                    int value = 1;
                }
                """;
        assertEquals(List.of(2), commentLines(source));
    }

    @Test
    void javadocIsReported() {
        String source = """
                /** Describes A. */
                class A {
                }
                """;
        assertEquals(List.of(1), commentLines(source));
    }

    @Test
    void commentMarkersInsideACommentAreNotReportedAgain() {
        String source = """
                int a = 1; // see /* here
                int b = 2; /* then // there */
                """;
        assertEquals(List.of(1, 2), commentLines(source));
    }

    @Test
    void doubleSlashInAStringLiteralIsNotAComment() {
        assertEquals(List.of(), commentLines("String url = \"https://example.org/a\";"));
    }

    @Test
    void blockCommentStartInAStringLiteralIsNotAComment() {
        assertEquals(List.of(), commentLines("String pattern = \"/* literal */\";"));
    }

    @Test
    void escapedQuoteDoesNotEndTheStringLiteral() {
        assertEquals(List.of(), commentLines("String text = \"say \\\" // still text\";"));
    }

    @Test
    void commentAfterAStringLiteralIsReported() {
        assertEquals(List.of(1), commentLines("String url = \"https://example.org\"; // real comment"));
    }

    @Test
    void markersInATextBlockAreNotComments() {
        String source = """
                class A {
                    String page = \"\"\"
                        <a href="https://example.org">// text</a>
                        /* also text */
                        \"\"\";
                }
                """;
        assertEquals(List.of(), commentLines(source));
    }

    @Test
    void commentAfterATextBlockIsReportedOnItsLine() {
        String source = """
                class A {
                    String page = \"\"\"
                        <p>text</p>
                        \"\"\";
                    int b = 2; // real
                }
                """;
        assertEquals(List.of(5), commentLines(source));
    }

    @Test
    void escapedDelimiterDoesNotEndATextBlock() {
        String source = """
                String page = \"\"\"
                    one \\\"\"\" // still inside
                    \"\"\";
                """;
        assertEquals(List.of(), commentLines(source));
    }

    @Test
    void quoteCharacterLiteralDoesNotStartAString() {
        assertEquals(List.of(1), commentLines("char quote = '\"'; // real comment"));
    }

    @Test
    void slashCharacterLiteralIsNotAComment() {
        assertEquals(List.of(), commentLines("char slash = '/'; char other = '/';"));
    }

    @Test
    void escapedQuoteCharacterLiteralIsHandled() {
        assertEquals(List.of(1), commentLines("char quote = '\\''; // real comment"));
    }

    @Test
    void divisionIsNotAComment() {
        assertEquals(List.of(), commentLines("int half = total / 2;"));
    }

    @Test
    void codeWithoutCommentsHasNoCommentLines() {
        String source = """
                package a;

                class A {
                    int value = 1;
                }
                """;
        assertEquals(List.of(), commentLines(source));
    }

    @Test
    void scannedCodeBlanksCommentsAndLiteralContentsButKeepsLines() {
        String source = "int a = 1; // note\nString s = \"import x;\";\n";
        String code = SourceScanner.scan(source).code();
        assertFalse(code.contains("note"));
        assertFalse(code.contains("import"));
        assertEquals(source.length(), code.length());
        assertEquals(2, code.split("\n").length);
    }

    @Test
    void nonAsciiCharactersAreReportedWithTheirLine() {
        String accented = "caf" + (char) 233;
        String source = "int a = 1;\nString s = \"" + accented + "\";\nint b = 2;\n";
        assertEquals(List.of(2), SourceScanner.nonAsciiLines(source));
    }

    @Test
    void asciiSourceHasNoNonAsciiLines() {
        assertEquals(List.of(), SourceScanner.nonAsciiLines("int a = 1;\nint b = 2;\n"));
    }
}
