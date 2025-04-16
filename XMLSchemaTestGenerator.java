import java.io.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.*;
import org.w3c.dom.*;

/**
 * ConstraintCrafter - XML Schema Test Data Generator
 * Generates positive and negative test cases for XML schemas based on cardinality and enumeration constraints
 */
public class ConstraintCrafter {
    
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
    
    // --- Add: Debug/Log Output Control ---
    public static boolean DEBUG_ENABLED = false;
    public static boolean LOG_ENABLED = true;

    public static void setDebug(boolean enabled) {
        DEBUG_ENABLED = enabled;
    }
    public static void setLog(boolean enabled) {
        LOG_ENABLED = enabled;
    }
    public static void debug(String msg) {
        if (DEBUG_ENABLED) System.out.println("[DEBUG] " + msg);
    }
    public static void log(String msg) {
        if (LOG_ENABLED) System.out.println("[LOG] " + msg);
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java ConstraintCrafter <schema-file> [--debug] [--nolog]");
            System.exit(1);
        }
        // Parse options
        for (String arg : args) {
            if (arg.equals("--debug")) setDebug(true);
            if (arg.equals("--nolog")) setLog(false);
        }
        String schemaFile = args[0];
        try {
            ConstraintCrafter generator = new ConstraintCrafter();
            generator.generateTests(schemaFile);
            log("Test data generation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // In the ConstraintCrafter constructor:
    public ConstraintCrafter() {
        schemaParser = new SchemaParser(this);
        xmlGenerator = new TestXmlGenerator(this, schemaParser);
        enumTestGenerator = new EnumerationTestGenerator(this);
        cardinalityTestGenerator = new CardinalityTestGenerator(this);
    }
    
    /**
     * Loads the schema and populates global elements and parser, but does NOT generate test files.
     */
    public void loadSchema(String schemaFile) throws Exception {
        Document schemaDoc = parseXmlFile(schemaFile);
        Element rootElement = schemaDoc.getDocumentElement();
        extractNamespaces(rootElement);
        String targetNamespace = rootElement.getAttribute("targetNamespace");
        if (targetNamespace != null && !targetNamespace.isEmpty()) {
            defaultNamespacePrefix = findPrefixForNamespace(targetNamespace);
        }
        Set<String> processedSchemas = new HashSet<>();
        List<Document> schemaDocuments = new ArrayList<>();
        schemaDocuments.add(schemaDoc);
        processedSchemas.add(schemaFile);
        schemaParser.collectIncludedSchemas(schemaDoc, schemaFile, processedSchemas, schemaDocuments);
        for (Document doc : schemaDocuments) {
            schemaParser.findAllGlobalElements(doc);
        }
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
            
            debug("Processing global element: " + elementName);
            
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
        debug("[DEBUG] ConstraintCrafter.findChildElement CALLED for child='" + localName + "' parent='" + (parent != null ? parent.getAttribute("name") : "<null>") + "'");
        if (parent == null) return null;
        // 1. Try direct child (original logic)
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                debug("[DEBUG] ConstraintCrafter.findChildElement: Found direct child '" + localName + "' in parent '" + parent.getAttribute("name") + "'");
                return (Element) child;
            }
        }
        // 2. If parent has a type attribute, resolve referenced complexType
        String typeAttr = parent.getAttribute("type");
        if (typeAttr != null && !typeAttr.isEmpty()) {
            String typeName = typeAttr.contains(":") ? typeAttr.split(":")[1] : typeAttr;
            Element typeDef = null;
            if (this instanceof ConstraintCrafter) {
                // Defensive: should always be true
                typeDef = ((ConstraintCrafter)this).getTypeDefinition(typeName);
            } else {
                // fallback: try static map
                typeDef = SchemaParser.typeDefinitions.get(typeName);
            }
            debug("[DEBUG] ConstraintCrafter.findChildElement: Resolved type attribute '" + typeAttr + "' to typeDef='" + (typeDef != null ? typeDef.getAttribute("name") : "<null>") + "'");
            if (typeDef != null) {
                // --- DEBUG: Print before searching for actual child ---
                debug("[DEBUG] ConstraintCrafter.findChildElement: Searching resolved complexType '" + typeDef.getAttribute("name") + "' for child '" + localName + "'");
                // Search <sequence> for child (namespace-aware)
                NodeList seqs = typeDef.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "sequence");
                debug("[DEBUG] ConstraintCrafter.findChildElement:   <sequence> has " + seqs.getLength() + " sequences");
                for (int s = 0; s < seqs.getLength(); s++) {
                    Element seq = (Element) seqs.item(s);
                    NodeList seqChildren = seq.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                    debug("[DEBUG] ConstraintCrafter.findChildElement:   <sequence> has " + seqChildren.getLength() + " <element> children");
                    for (int j = 0; j < seqChildren.getLength(); j++) {
                        Element el = (Element) seqChildren.item(j);
                        String name = el.getAttribute("name");
                        String ref = el.getAttribute("ref");
                        debug("[DEBUG] ConstraintCrafter.findChildElement:     candidate <element> name='" + name + "', ref='" + ref + "'");
                        if ((!name.isEmpty() && name.equals(localName)) || (!ref.isEmpty() && (ref.equals(localName) || ref.endsWith(":" + localName)))) {
                            debug("[DEBUG] ConstraintCrafter.findChildElement: MATCHED in <sequence>: '" + name + "' or ref='" + ref + "'");
                            return el;
                        }
                    }
                }
                // Search direct children of complexType (namespace-aware)
                NodeList directChildren = typeDef.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                debug("[DEBUG] ConstraintCrafter.findChildElement:   complexType has " + directChildren.getLength() + " direct <element> children");
                for (int j = 0; j < directChildren.getLength(); j++) {
                    Element el = (Element) directChildren.item(j);
                    String name = el.getAttribute("name");
                    String ref = el.getAttribute("ref");
                    debug("[DEBUG] ConstraintCrafter.findChildElement:     direct candidate <element> name='" + name + "', ref='" + ref + "'");
                    if ((!name.isEmpty() && name.equals(localName)) || (!ref.isEmpty() && (ref.equals(localName) || ref.endsWith(":" + localName)))) {
                        debug("[DEBUG] ConstraintCrafter.findChildElement: MATCHED in <complexType> direct: '" + name + "' or ref='" + ref + "'");
                        return el;
                    }
                }
            }
        }
        // 3. Fallback: try global element definitions (if available)
        if (this instanceof ConstraintCrafter) {
            Element global = ((ConstraintCrafter)this).getGlobalElementDefinitions().get(localName);
            if (global != null) {
                debug("[DEBUG] ConstraintCrafter.findChildElement: Fallback to global element definition for '" + localName + "'");
                return global;
            }
        }
        debug("[DEBUG] ConstraintCrafter.findChildElement: NO MATCH for child '" + localName + "' in parent '" + (parent.getAttribute("name")) + "'");
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
    
    // Add this method to resolve type definitions by name
    public org.w3c.dom.Element getTypeDefinition(String typeName) {
        // Try to retrieve from SchemaParser's static map, or from an internal map if available
        // (Assume SchemaParser.typeDefinitions is public static or provide a getter)
        if (SchemaParser.typeDefinitions != null && SchemaParser.typeDefinitions.containsKey(typeName)) {
            debug("[DEBUG] ConstraintCrafter.getTypeDefinition: Found typeDef for '" + typeName + "'");
            return SchemaParser.typeDefinitions.get(typeName);
        }
        debug("[DEBUG] ConstraintCrafter.getTypeDefinition: No typeDef found for '" + typeName + "'");
        return null;
    }
}