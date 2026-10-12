package tlm.dev.lectern.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SeamRulesTest {

    private static final List<String> BEHAVIOURAL = List.of("manifest", "fetch", "extract", "normalize", "publish");
    private static final List<String> TRANSVERSE = List.of("model", "json", "http", "process", "files");

    private static String importing(String... imports) {
        StringBuilder source = new StringBuilder("package tlm.dev.lectern.pipeline.x;\n\n");
        for (String imported : imports) {
            source.append("import ").append(imported).append(";\n");
        }
        return source.append("\nclass X {\n}\n").toString();
    }

    @Test
    void familyImportingAnotherFamilyIsReported() {
        String source = importing("tlm.dev.lectern.pipeline.fetch.JarClient");
        assertEquals(
                List.of("normalize/Reader.java: normalize -> fetch (tlm.dev.lectern.pipeline.fetch.JarClient)"),
                SeamRules.violations("normalize/Reader.java", source));
    }

    @Test
    void everyBehaviouralFamilyIsIsolatedFromTheOthers() {
        for (String from : BEHAVIOURAL) {
            for (String to : BEHAVIOURAL) {
                String source = importing("tlm.dev.lectern.pipeline." + to + ".Thing");
                int expected = from.equals(to) ? 0 : 1;
                assertEquals(expected, SeamRules.violations(from + "/Source.java", source).size(), from + " -> " + to);
            }
        }
    }

    @Test
    void behaviouralFamilyMayImportEveryTransverseFamily() {
        for (String from : BEHAVIOURAL) {
            for (String to : TRANSVERSE) {
                String source = importing("tlm.dev.lectern.pipeline." + to + ".Thing");
                assertEquals(List.of(), SeamRules.violations(from + "/Source.java", source), from + " -> " + to);
            }
        }
    }

    @Test
    void transverseFamilyImportingAnyOtherFamilyIsReported() {
        List<String> everyone = List.of("model", "json", "http", "process", "files", "manifest", "fetch", "extract", "normalize", "publish");
        for (String from : TRANSVERSE) {
            for (String to : everyone) {
                String source = importing("tlm.dev.lectern.pipeline." + to + ".Thing");
                int expected = from.equals(to) ? 0 : 1;
                assertEquals(expected, SeamRules.violations(from + "/Source.java", source).size(), from + " -> " + to);
            }
        }
    }

    @Test
    void familyImportingARootClassIsReported() {
        String source = importing("tlm.dev.lectern.pipeline.PipelineMain");
        assertEquals(
                List.of("manifest/Service.java: manifest -> root (tlm.dev.lectern.pipeline.PipelineMain)"),
                SeamRules.violations("manifest/Service.java", source));
    }

    @Test
    void familyImportingAStaticMemberOfARootClassIsReported() {
        String source = "import static tlm.dev.lectern.pipeline.PipelineMain.LIST_SIZE;\n";
        assertEquals(1, SeamRules.violations("model/Value.java", source).size());
    }

    @Test
    void rootClassMayImportEveryFamily() {
        String source = importing(
                "tlm.dev.lectern.pipeline.model.VersionId",
                "tlm.dev.lectern.pipeline.json.JsonTextReader",
                "tlm.dev.lectern.pipeline.manifest.ManifestClient",
                "tlm.dev.lectern.pipeline.fetch.JarClient",
                "tlm.dev.lectern.pipeline.publish.DataWriter");
        assertEquals(List.of(), SeamRules.violations("PipelineMain.java", source));
    }

    @Test
    void jacksonInsideJsonIsAccepted() {
        String source = importing("tools.jackson.core.JsonParser", "tools.jackson.core.json.JsonFactory");
        assertEquals(List.of(), SeamRules.violations("json/Reader.java", source));
    }

    @Test
    void jacksonOutsideJsonIsReported() {
        for (String family : List.of("model", "http", "process", "files", "manifest", "fetch", "extract", "normalize", "publish")) {
            String source = importing("tools.jackson.core.JsonParser");
            assertEquals(
                    List.of(family + "/Reader.java: tools.jackson outside json (tools.jackson.core.JsonParser)"),
                    SeamRules.violations(family + "/Reader.java", source),
                    family);
        }
    }

    @Test
    void jacksonInTheRootIsReported() {
        String source = importing("tools.jackson.core.JsonParser");
        assertEquals(1, SeamRules.violations("PipelineMain.java", source).size());
    }

    @Test
    void oldJacksonNamespaceIsAnUndeclaredThirdParty() {
        String source = importing("com.fasterxml.jackson.core.JsonParser");
        assertEquals(
                List.of("json/Reader.java: undeclared third-party import (com.fasterxml.jackson.core.JsonParser)"),
                SeamRules.violations("json/Reader.java", source));
    }

    @Test
    void jdkImportsAreAccepted() {
        String source = importing("java.util.List", "java.net.http.HttpClient", "javax.xml.stream.XMLStreamWriter");
        assertEquals(List.of(), SeamRules.violations("manifest/Client.java", source));
    }

    @Test
    void undeclaredFamilyDirectoryIsReported() {
        assertEquals(
                List.of("misc/Thing.java: undeclared family misc"),
                SeamRules.violations("misc/Thing.java", importing("java.util.List")));
    }

    @Test
    void importOfAnUndeclaredOwnPackageIsReported() {
        String source = importing("tlm.dev.lectern.pipeline.misc.Thing");
        assertEquals(
                List.of("manifest/Planner.java: undeclared family misc (tlm.dev.lectern.pipeline.misc.Thing)"),
                SeamRules.violations("manifest/Planner.java", source));
    }

    @Test
    void importInsideATextBlockIsIgnored() {
        String source = """
                package tlm.dev.lectern.pipeline.manifest;

                class Page {
                    String text = \"\"\"
                import tlm.dev.lectern.pipeline.fetch.JarClient;
                \"\"\";
                }
                """;
        assertEquals(List.of(), SeamRules.violations("manifest/Page.java", source));
    }

    @Test
    void wildcardImportOfAnotherFamilyIsReported() {
        String source = "import tlm.dev.lectern.pipeline.publish.*;\n";
        assertEquals(1, SeamRules.violations("manifest/Planner.java", source).size());
    }
}
