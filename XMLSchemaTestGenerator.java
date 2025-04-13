import java.io.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.*;
import org.w3c.dom.*;

/**
 * Generic XML Schema Test Data Generator
 * Generates positive and negative test cases for XML schemas based on cardinality and enumeration constraints
 */
public class XMLSchemaTestGenerator {
    
    private static final Random random = new Random();
    private static final Map<String, String> NAMESPACE_MAP = new HashMap<>();
    private final Map<String, List<ElementInfo>> globalElementsMap = new HashMap<>();
    private final Map<String, Element> globalElementDefinitions = new HashMap<>();
    private final Map<String, List<String>> enumValueCache = new HashMap<>();
    private String defaultNamespacePrefix = null;
    private SchemaParser schemaParser;
    private TestXmlGenerator xmlGenerator;
    private EnumerationTestGenerator enumTestGenerator;
    private CardinalityTestGenerator cardinalityTestGenerator;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java XMLSchemaTestGenerator <schema-file>");
            System.exit(1);
        }
        
        String schemaFile = args[0];
        try {
            XMLSchemaTestGenerator generator = new XMLSchemaTestGenerator();
            generator.generateTests(schemaFile);
            System.out.println("Test data generation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // In the XMLSchemaTestGenerator constructor:
    public XMLSchemaTestGenerator() {
        schemaParser = new SchemaParser(this);
        xmlGenerator = new TestXmlGenerator(this);
        enumTestGenerator = new EnumerationTestGenerator(this);
        cardinalityTestGenerator = new CardinalityTestGenerator(this);
    }
    
    /**
     * Generate test cases for the given schema file
     */
    public void generateTests(String schemaFile) throws Exception {
        // Parse the schema
        Document schemaDoc = parseXmlFile(schemaFile);
        Element rootElement = schemaDoc.getDocumentElement();
        
        // Extract namespace information
        extractNamespaces(rootElement);
        String targetNamespace = rootElement.getAttribute("targetNamespace");
        if (targetNamespace != null && !targetNamespace.isEmpty()) {
            defaultNamespacePrefix = findPrefixForNamespace(targetNamespace);
        }
        
        // Create output directories
        createDirectories();
        
        // Collect all schema files (including imports and includes)
        Set<String> processedSchemas = new HashSet<>();
        List<Document> schemaDocuments = new ArrayList<>();
        schemaDocuments.add(schemaDoc);
        processedSchemas.add(schemaFile);
        
        // Process includes and imports
        schemaParser.collectIncludedSchemas(schemaDoc, schemaFile, processedSchemas, schemaDocuments);
        
        // Find and cache all global elements from all schema documents
        for (Document doc : schemaDocuments) {
            schemaParser.findAllGlobalElements(doc);
        }
        
        // Process global elements
        processGlobalElements(targetNamespace, schemaFile);
    }
    
    /**
     * Process all global elements for testing
     */
    private void processGlobalElements(String targetNamespace, String schemaFile) throws Exception {
        for (Map.Entry<String, Element> entry : globalElementDefinitions.entrySet()) {
            String elementName = entry.getKey();
            Element elementDef = entry.getValue();
            
            System.out.println("Processing global element: " + elementName);
            
            // Process cardinality tests for child elements
            cardinalityTestGenerator.generateCardinalityTests(elementName, elementDef, targetNamespace, schemaFile);
            
            // Process enumeration tests
            enumTestGenerator.generateEnumerationTests(elementName, elementDef, targetNamespace, schemaFile);
        }
    }
    
    /**
     * Extract namespace declarations from schema
     */
    public void extractNamespaces(Element schemaElement) {
        NamedNodeMap attributes = schemaElement.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String name = attr.getNodeName();
            if (name.startsWith("xmlns:")) {
                String prefix = name.substring(6);
                String uri = attr.getNodeValue();
                NAMESPACE_MAP.put(prefix, uri);
            }
        }
    }
    
    /**
     * Create output directories for test files
     */
    private void createDirectories() {
        new File("test-output/positive/cardinality").mkdirs();
        new File("test-output/negative/cardinality").mkdirs();
        new File("test-output/positive/enumeration").mkdirs();
        new File("test-output/negative/enumeration").mkdirs();
    }
    
    /**
     * Find prefix for a namespace URI
     */
    public String findPrefixForNamespace(String namespaceUri) {
        for (Map.Entry<String, String> entry : NAMESPACE_MAP.entrySet()) {
            if (entry.getValue().equals(namespaceUri)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Find child element by local name
     */
    public Element findChildElement(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && 
                localName.equals(child.getLocalName())) {
                return (Element) child;
            }
        }
        return null;
    }
    
    /**
     * Parse XML file into DOM
     */
    public Document parseXmlFile(String fileName) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new File(fileName));
    }
    
    /**
     * Write content to file
     */
    public void writeTestFile(String fileName, String content) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            writer.print(content);
            System.out.println("Created test file: " + fileName);
        }
    }
    
    /**
     * Validate XML against schema
     */
    public void validateAgainstSchema(String xmlFile, String schemaFile, boolean expectValid) {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            
            // Set up basic properties for schema resolution
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            
            Schema schema = factory.newSchema(new File(schemaFile));
            Validator validator = schema.newValidator();
            
            validator.validate(new StreamSource(new File(xmlFile)));
            
            // If we get here, validation passed
            if (!expectValid) {
                System.out.println("WARNING: " + xmlFile + " passed validation but was expected to fail");
            }
        } catch (Exception e) {
            // Validation failed
            if (expectValid) {
                System.out.println("WARNING: " + xmlFile + " failed validation but was expected to pass: " + e.getMessage());
            }
        }
    }
    
    // Getters for fields needed by the other classes
    public Map<String, String> getNamespaceMap() {
        return NAMESPACE_MAP;
    }
    
    public Map<String, List<ElementInfo>> getGlobalElementsMap() {
        return globalElementsMap;
    }
    
    public Map<String, Element> getGlobalElementDefinitions() {
        return globalElementDefinitions;
    }
    
    public Map<String, List<String>> getEnumValueCache() {
        return enumValueCache;
    }
    
    public String getDefaultNamespacePrefix() {
        return defaultNamespacePrefix;
    }
}