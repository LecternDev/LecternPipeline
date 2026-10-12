package tlm.dev.lectern.pipeline.fetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tlm.dev.lectern.pipeline.json.JsonTextReader;
import tlm.dev.lectern.pipeline.model.Sha1;
import tlm.dev.lectern.pipeline.model.VersionId;

class VersionJsonReaderTest {

    private static final VersionId ID = new VersionId("99.1");
    private static final String SHA1 = "0123456789abcdef0123456789abcdef01234567";

    private final VersionJsonReader reader = new VersionJsonReader(new JsonTextReader());

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String withServer(String serverMembers) {
        return "{\"id\": \"99.1\", \"downloads\": {\"client\": {\"size\": 1}, \"server\": {" + serverMembers + "}}}";
    }

    private static String validServer() {
        return "\"sha1\": \"" + SHA1 + "\", \"size\": 1234, \"url\": \"https://downloads.example.invalid/server.jar\"";
    }

    @Test
    void serverDownloadIsReadAndOtherMembersAreIgnored() {
        ServerDownload download = reader.readServerDownload(ID, bytes(withServer(validServer())));
        assertEquals(new ServerDownload(new Sha1(SHA1), 1234, URI.create("https://downloads.example.invalid/server.jar")), download);
    }

    @Test
    void memberOrderDoesNotMatter() {
        String reordered = "{\"downloads\": {\"server\": {\"url\": \"https://downloads.example.invalid/s.jar\", \"size\": 5, \"sha1\": \"" + SHA1 + "\"}}}";
        assertEquals(5, reader.readServerDownload(ID, bytes(reordered)).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"downloads\": []}",
            "{\"downloads\": {}}",
            "{\"downloads\": {\"client\": {}}}",
            "{\"downloads\": {\"server\": 3}}"})
    void versionJsonWithoutAServerDownloadObjectIsRejectedNamingTheVersion(String text) {
        VersionJsonFormatException failure = assertThrows(VersionJsonFormatException.class, () -> reader.readServerDownload(ID, bytes(text)));
        assertTrue(failure.getMessage().contains("Version 99.1"), failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"size\": 1234, \"url\": \"https://downloads.example.invalid/s.jar\"",
            "\"sha1\": \"0123456789abcdef0123456789abcdef01234567\", \"url\": \"https://downloads.example.invalid/s.jar\"",
            "\"sha1\": \"0123456789abcdef0123456789abcdef01234567\", \"size\": 1234",
            "\"sha1\": 5, \"size\": 1234, \"url\": \"https://downloads.example.invalid/s.jar\"",
            "\"sha1\": \"0123456789abcdef0123456789abcdef01234567\", \"size\": \"1234\", \"url\": \"https://downloads.example.invalid/s.jar\"",
            "\"sha1\": \"0123456789abcdef0123456789abcdef01234567\", \"size\": 1234, \"url\": 7"})
    void missingOrMistypedMembersAreRejected(String serverMembers) {
        assertThrows(VersionJsonFormatException.class, () -> reader.readServerDownload(ID, bytes(withServer(serverMembers))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "1.5", "1e3", "12345678901234567890"})
    void sizeThatIsNotAPositiveIntegerIsRejected(String size) {
        String members = "\"sha1\": \"" + SHA1 + "\", \"size\": " + size + ", \"url\": \"https://downloads.example.invalid/s.jar\"";
        assertThrows(VersionJsonFormatException.class, () -> reader.readServerDownload(ID, bytes(withServer(members))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT-A-HASH", "0123456789ABCDEF0123456789ABCDEF01234567", "0123"})
    void sha1ThatIsNotFortyLowercaseHexCharactersIsRejected(String sha1) {
        String members = "\"sha1\": \"" + sha1 + "\", \"size\": 5, \"url\": \"https://downloads.example.invalid/s.jar\"";
        assertThrows(VersionJsonFormatException.class, () -> reader.readServerDownload(ID, bytes(withServer(members))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"relative/path.jar", "file:///etc/passwd", "ftp://downloads.example.invalid/s.jar", "https:///nohost", "not a url"})
    void urlThatIsNotAWebUrlWithAHostIsRejected(String url) {
        String members = "\"sha1\": \"" + SHA1 + "\", \"size\": 5, \"url\": \"" + url + "\"";
        assertThrows(VersionJsonFormatException.class, () -> reader.readServerDownload(ID, bytes(withServer(members))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not json", "[]", "{\"downloads\": {\"server\": {\"size\": 1, \"size\": 2}}}"})
    void textThatIsNotAJsonObjectWithoutDuplicatesIsRejected(String text) {
        VersionJsonFormatException failure = assertThrows(VersionJsonFormatException.class, () -> reader.readServerDownload(ID, bytes(text)));
        assertTrue(failure.getMessage().contains("Version 99.1"), failure.getMessage());
    }
}
