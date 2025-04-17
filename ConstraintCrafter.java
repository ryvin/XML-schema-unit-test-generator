import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Main entry point for ConstraintCrafter.
 * Orchestrates schema parsing, test generation, output, and (optional) validation.
 */
public class ConstraintCrafter {
    // Store global element definitions and their child info
    private Map<String, Element> globalElementDefinitions = new HashMap<>();
    private Map<String, List<ElementInfo>> globalElementsMap = new HashMap<>();

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java ConstraintCrafter <schema.xsd> <outputDir>");
            System.exit(1);
        }
        ConstraintCrafter crafter = new ConstraintCrafter();
        File xsdFile = new File(args[0]);
        String outputDir = args[1];

        // Parse schema
        SchemaParser parser = new SchemaParser(crafter);
        ConstraintModel model = parser.parseSchema(xsdFile);

        // Load XML Document and collect definitions
        Document schemaDoc = null;
        try {
            schemaDoc = crafter.parseXmlFile(xsdFile.getAbsolutePath());
            parser.collectIncludedSchemas(schemaDoc, xsdFile.getAbsolutePath(), new HashSet<>(), new ArrayList<>());
            parser.findAllGlobalElements(schemaDoc);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Enumeration test cases
        TestCaseGenerator caseGen = new TestCaseGenerator();
        List<TestCase> positiveEnumTests = caseGen.generatePositiveTestCases(model);
        List<TestCase> negativeEnumTests = caseGen.generateNegativeTestCases(model);

        // Save enumeration tests
        OutputManager outputManager = new OutputManager(outputDir);
        outputManager.saveAllTestCases(positiveEnumTests);
        outputManager.saveAllTestCases(negativeEnumTests);
        System.out.println("[ConstraintCrafter] Enumeration: +" + positiveEnumTests.size() + " tests, -" + negativeEnumTests.size() + " tests");

        // Cardinality tests
        CardinalityTestGenerator cardGen = new CardinalityTestGenerator(crafter);
        String targetNamespace = schemaDoc != null ? schemaDoc.getDocumentElement().getAttribute("targetNamespace") : "";
        for (String elementName : crafter.getGlobalElementDefinitions().keySet()) {
            Element element = crafter.getGlobalElementDefinitions().get(elementName);
            try {
                cardGen.generateCardinalityTests(elementName, element, targetNamespace, xsdFile.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Structured enumeration tests
        EnumerationTestGenerator enumGen = new EnumerationTestGenerator(crafter);
        for (String elementName : crafter.getGlobalElementDefinitions().keySet()) {
            Element element = crafter.getGlobalElementDefinitions().get(elementName);
            try {
                enumGen.generateEnumerationTests(elementName, element, targetNamespace, xsdFile.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println("[ConstraintCrafter] Cardinality and structured enumeration tests generated.");
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

    // Get child elements map
    public Map<String, List<ElementInfo>> getGlobalElementsMap() {
        return this.globalElementsMap;
    }

    // Get global element definitions map
    public Map<String, Element> getGlobalElementDefinitions() {
        return this.globalElementDefinitions;
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
