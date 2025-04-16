import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.w3c.dom.Element;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Main entry point for ConstraintCrafter.
 * Orchestrates schema parsing, test generation, output, and (optional) validation.
 */
public class ConstraintCrafter {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java ConstraintCrafter <schema.xsd> <outputDir>");
            System.exit(1);
        }
        File xsdFile = new File(args[0]);
        String outputDir = args[1];

        // Parse schema
        SchemaParser parser = new SchemaParser();
        ConstraintModel model = parser.parseSchema(xsdFile);

        // Generate test cases
        TestCaseGenerator generator = new TestCaseGenerator();
        List<TestCase> positiveTests = generator.generatePositiveTestCases(model);
        List<TestCase> negativeTests = generator.generateNegativeTestCases(model);

        System.out.println("[ConstraintCrafter] Positive test cases: " + positiveTests.size());
        System.out.println("[ConstraintCrafter] Negative test cases: " + negativeTests.size());
        System.out.println("[ConstraintCrafter] Output directory: " + outputDir);
        if (positiveTests.isEmpty() && negativeTests.isEmpty()) {
            System.out.println("[ConstraintCrafter] WARNING: No test cases generated. Check your schema or parsing logic.");
        }

        // Save test cases
        OutputManager outputManager = new OutputManager(outputDir);
        outputManager.saveAllTestCases(positiveTests);
        outputManager.saveAllTestCases(negativeTests);

        // (Optional) Validation step can be added here
        System.out.println("Test case generation complete. See output in: " + outputDir);
    }

    // Write XML content to a file
    public void writeTestFile(String fileName, String xml) {
        try {
            Files.write(Paths.get(fileName), xml.getBytes(StandardCharsets.UTF_8));
            log("[ConstraintCrafter] Wrote test file: " + fileName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Stub for schema validation
    public void validateAgainstSchema(String fileName, String schemaFile, boolean expectValid) {
        log("[ConstraintCrafter] validateAgainstSchema called for " + fileName + ", schema " + schemaFile + ", expectValid=" + expectValid);
    }

    // Get child elements map (stub)
    public Map<String, List<ElementInfo>> getGlobalElementsMap() {
        return new HashMap<>();
    }

    // Get global element definitions map (stub)
    public Map<String, Element> getGlobalElementDefinitions() {
        return new HashMap<>();
    }

    // Get namespace prefix map (stub)
    public Map<String, String> getNamespaceMap() {
        return new HashMap<>();
    }

    // Default namespace prefix (stub)
    public String getDefaultNamespacePrefix() {
        return "";
    }

    // Logging methods
    public static void log(String msg) {
        System.out.println(msg);
    }

    public static void debug(String msg) {
        System.out.println(msg);
    }

    // Parse XML file to Document
    public Document parseXmlFile(String filePath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new java.io.File(filePath));
    }

    // Extract namespace declarations from an element
    public void extractNamespaces(Element element) {
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            String name = attr.getNodeName();
            if (name.startsWith("xmlns:")) {
                String prefix = name.substring(6);
                getNamespaceMap().put(prefix, attr.getNodeValue());
                debug("Added namespace prefix mapping: " + prefix + " -> " + attr.getNodeValue());
            }
        }
    }

    // Delegate to SchemaParser for finding child elements
    public Element findChildElement(Element parent, String localName) {
        return SchemaParser.findChildElement(parent, localName);
    }
}
