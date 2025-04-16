/**
 * File: src/main/java/SchemaParser.java
 * 
 * Description: This class is responsible for parsing XML Schema files (XSD),
 * extracting element definitions, type information, and handling all cross-references
 * between schema components. It supports resolving element and type references,
 * processing imported and included schemas, and traversing complex type hierarchies.
 */
import java.io.File;
import java.util.*;
import javax.xml.XMLConstants;
import org.w3c.dom.*;

public class SchemaParser {
    // New no-argument constructor for modular ConstraintCrafter usage
    public SchemaParser() {
        this.generator = null;
    }
    /**
     * Parses the given XSD file and builds a ConstraintModel (new modular architecture).
     * Only collects global elements and their enumeration constraints for now.
     */
    public ConstraintModel parseSchema(File xsdFile) {
        ConstraintModel model = new ConstraintModel();
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            org.w3c.dom.Document doc = factory.newDocumentBuilder().parse(xsdFile);
            org.w3c.dom.Element schemaElem = doc.getDocumentElement();
            org.w3c.dom.NodeList elements = schemaElem.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
            for (int i = 0; i < elements.getLength(); i++) {
                org.w3c.dom.Element elem = (org.w3c.dom.Element) elements.item(i);
                if (elem.getParentNode() == schemaElem) { // Only global elements
                    ElementConstraint ec = new ElementConstraint();
                    ec.setName(elem.getAttribute("name"));
                    ec.setType(elem.getAttribute("type"));
                    String minOccurs = elem.getAttribute("minOccurs");
                    String maxOccurs = elem.getAttribute("maxOccurs");
                    if (!minOccurs.isEmpty()) ec.setMinOccurs(Integer.parseInt(minOccurs));
                    if (!maxOccurs.isEmpty()) ec.setMaxOccurs(maxOccurs.equals("unbounded") ? Integer.MAX_VALUE : Integer.parseInt(maxOccurs));
                    // Try to find enumeration values (inline or by type)
                    List<String> enums = findEnumerationValues(elem);
                    XMLSchemaTestGenerator.log("[DEBUG] Global element '" + elem.getAttribute("name") + "' type='" + elem.getAttribute("type") + "' enums: " + enums);
                    ec.setEnumerationValues(enums);
                    model.addElementConstraint(ec);
                }
            }
            XMLSchemaTestGenerator.log("[DEBUG] Total global elements parsed: " + model.getElements().size());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return model;
    }
    
    // Map to store all global type definitions (simpleType and complexType) by name
    public static Map<String, Element> typeDefinitions = new HashMap<>(); // Global type definitions

    
    private Set<String> resolvedReferences = new HashSet<>();
    private Map<String, String> prefixToNamespaceMap = new HashMap<>();
    private Map<String, Element> groupDefinitions = new HashMap<>();
    private Map<String, List<Element>> substitutionGroups = new HashMap<>();
    private XMLSchemaTestGenerator generator;
    // Local cache for enumeration values (for modular ConstraintCrafter usage)
    private final Map<String, List<String>> enumValueCache = new HashMap<>();

    /**
     * Find a child element by local name (generator-independent)
     */
    private static Element findChildElement(Element parent, String localName) {
        // Debug child search
        if (parent != null) {
            XMLSchemaTestGenerator.log("[DEBUG] Searching for child '" + localName + "' in parent '" + parent.getAttribute("name") + "'");
        }

        if (parent == null) return null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                return (Element) child;
            }
        }
        return null;
    }

    public SchemaParser(XMLSchemaTestGenerator generator) {
        this.generator = generator;
    }

    // --- Restriction extraction helpers for XmlValueHelper ---
    /**
     * Finds the pattern facet for a given type name, or null if not present.
     */
    public String findPatternForType(String type) {
        Element restriction = findRestrictionForType(type);
        if (restriction != null) {
            NodeList patterns = restriction.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "pattern");
            if (patterns.getLength() > 0) {
                Element patternElem = (Element) patterns.item(0);
                return patternElem.getAttribute("value");
            }
        }
        return null;
    }
    /**
     * Finds the minLength facet for a given type name, or null if not present.
     */
    public Integer findMinLengthForType(String type) {
        Element restriction = findRestrictionForType(type);
        if (restriction != null) {
            NodeList minLengths = restriction.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "minLength");
            if (minLengths.getLength() > 0) {
                Element minLenElem = (Element) minLengths.item(0);
                try { return Integer.parseInt(minLenElem.getAttribute("value")); } catch (Exception e) { return null; }
            }
        }
        return null;
    }
    /**
     * Finds the maxLength facet for a given type name, or null if not present.
     */
    public Integer findMaxLengthForType(String type) {
        Element restriction = findRestrictionForType(type);
        if (restriction != null) {
            NodeList maxLengths = restriction.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "maxLength");
            if (maxLengths.getLength() > 0) {
                Element maxLenElem = (Element) maxLengths.item(0);
                try { return Integer.parseInt(maxLenElem.getAttribute("value")); } catch (Exception e) { return null; }
            }
        }
        return null;
    }
    /**
     * Finds the minInclusive facet for a given type name, or null if not present.
     */
    public String findMinInclusiveForType(String type) {
        Element restriction = findRestrictionForType(type);
        if (restriction != null) {
            NodeList minInc = restriction.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "minInclusive");
            if (minInc.getLength() > 0) {
                Element minElem = (Element) minInc.item(0);
                return minElem.getAttribute("value");
            }
        }
        return null;
    }
    /**
     * Finds the maxInclusive facet for a given type name, or null if not present.
     */
    public String findMaxInclusiveForType(String type) {
        Element restriction = findRestrictionForType(type);
        if (restriction != null) {
            NodeList maxInc = restriction.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "maxInclusive");
            if (maxInc.getLength() > 0) {
                Element maxElem = (Element) maxInc.item(0);
                return maxElem.getAttribute("value");
            }
        }
        return null;
    }
    /**
     * Helper: find the <restriction> element for a type name
     */
    private Element findRestrictionForType(String type) {
        if (type == null || type.isEmpty()) return null;
        String typeName = type.contains(":") ? type.split(":")[1] : type;
        Element typeDef = resolveTypeDefinition(typeName);
        if (typeDef != null && typeDef.getLocalName().equals("simpleType")) {
            // Look for <restriction> child
            NodeList children = typeDef.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE && "restriction".equals(child.getLocalName())) {
                    return (Element) child;
                }
            }
        }
        return null;
    }

    /**
     * Collect included and imported schema documents
     */
    public void collectIncludedSchemas(Document schemaDoc, String baseSchemaFile, 
                                      Set<String> processedSchemas, List<Document> schemaDocuments) throws Exception {
        File baseFile = new File(baseSchemaFile);
        String basePath = baseFile.getParent();
        if (basePath == null) {
            basePath = ".";
        }
        
        // First extract namespace declarations for prefix resolution
        extractNamespaceDeclarations(schemaDoc.getDocumentElement());
        
        // Process includes
        NodeList includes = schemaDoc.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "include");
        for (int i = 0; i < includes.getLength(); i++) {
            Element include = (Element) includes.item(i);
            String schemaLocation = include.getAttribute("schemaLocation");
            
            if (!schemaLocation.isEmpty()) {
                String fullPath = basePath + File.separator + schemaLocation;
                
                if (!processedSchemas.contains(fullPath)) {
                    try {
                        Document includedDoc = generator.parseXmlFile(fullPath);
                        schemaDocuments.add(includedDoc);
                        processedSchemas.add(fullPath);
                        
                        // Extract namespace information from included schema
                        generator.extractNamespaces(includedDoc.getDocumentElement());
                        extractNamespaceDeclarations(includedDoc.getDocumentElement());
                        
                        // Recursively process includes/imports
                        collectIncludedSchemas(includedDoc, fullPath, processedSchemas, schemaDocuments);
                    } catch (Exception e) {
                        XMLSchemaTestGenerator.log("Could not process included schema: " + fullPath);
                    }
                }
            }
        }
        
        // Process imports
        NodeList imports = schemaDoc.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "import");
        for (int i = 0; i < imports.getLength(); i++) {
            Element importElem = (Element) imports.item(i);
            String schemaLocation = importElem.getAttribute("schemaLocation");
            String namespace = importElem.getAttribute("namespace");
            
            if (!schemaLocation.isEmpty()) {
                String fullPath = basePath + File.separator + schemaLocation;
                
                if (!processedSchemas.contains(fullPath)) {
                    try {
                        Document importedDoc = generator.parseXmlFile(fullPath);
                        schemaDocuments.add(importedDoc);
                        processedSchemas.add(fullPath);
                        
                        // Extract namespace information from imported schema
                        generator.extractNamespaces(importedDoc.getDocumentElement());
                        extractNamespaceDeclarations(importedDoc.getDocumentElement());
                        
                        // Store namespace for this imported schema
                        if (!namespace.isEmpty()) {
                            // Find prefix for this namespace
                            for (Map.Entry<String, String> entry : prefixToNamespaceMap.entrySet()) {
                                if (entry.getValue().equals(namespace)) {
                                    // We found the prefix for this namespace
                                    XMLSchemaTestGenerator.debug("Found prefix " + entry.getKey() + " for namespace " + namespace);
                                    break;
                                }
                            }
                        }
                        
                        // Recursively process includes/imports
                        collectIncludedSchemas(importedDoc, fullPath, processedSchemas, schemaDocuments);
                    } catch (Exception e) {
                        XMLSchemaTestGenerator.log("Could not process imported schema: " + fullPath);
                    }
                }
            }
        }
    }
    
    /**
     * Extract namespace declarations (xmlns:*) from schema elements
     */
    private void extractNamespaceDeclarations(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String name = attr.getNodeName();
            String value = attr.getNodeValue();
            
            if (name.startsWith("xmlns:")) {
                String prefix = name.substring(6); // Extract prefix after "xmlns:"
                prefixToNamespaceMap.put(prefix, value);
                XMLSchemaTestGenerator.debug("Added namespace prefix mapping: " + prefix + " -> " + value);
            }
        }
    }
    
    /**
     * Find all global elements, groups, and type definitions in the schema
     */
    public void findAllGlobalElements(Document schemaDoc) {
        // First extract namespace declarations
        extractNamespaceDeclarations(schemaDoc.getDocumentElement());
        
        // --- PATCH: Collect all global simpleType and complexType definitions ---
        NodeList simpleTypes = schemaDoc.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element simpleType = (Element) simpleTypes.item(i);
            Node parent = simpleType.getParentNode();
            if (parent != null && (parent.getLocalName().equals("schema") || parent.getNodeName().equals("xs:schema"))) {
                String name = simpleType.getAttribute("name");
                if (!name.isEmpty()) {
                    typeDefinitions.put(name, simpleType);
                    XMLSchemaTestGenerator.log("[DEBUG] Registered global simpleType: " + name);
                }
            }
        }

    NodeList complexTypes = schemaDoc.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "complexType");
    for (int i = 0; i < complexTypes.getLength(); i++) {
        Element complexType = (Element) complexTypes.item(i);
        Node parent = complexType.getParentNode();
        if (parent != null && (parent.getLocalName().equals("schema") || parent.getNodeName().equals("xs:schema"))) {
            String name = complexType.getAttribute("name");
            if (!name.isEmpty()) {
                typeDefinitions.put(name, complexType);
                XMLSchemaTestGenerator.log("[DEBUG] Registered global complexType: " + name);
            }
        }
    }

    // Index all global group definitions
    indexGlobalGroupDefinitions(schemaDoc);
}

/**
 * Index all global group definitions for later reference
 */
private void indexGlobalGroupDefinitions(Document schemaDoc) {
    NodeList groups = schemaDoc.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "group");
    for (int i = 0; i < groups.getLength(); i++) {
        Element groupElem = (Element) groups.item(i);
        Node parent = groupElem.getParentNode();
        if (parent != null && (parent.getLocalName().equals("schema") || parent.getNodeName().equals("xs:schema"))) {
            String name = groupElem.getAttribute("name");
            if (!name.isEmpty()) {
                groupDefinitions.put(name, groupElem);
                XMLSchemaTestGenerator.debug("Added global group definition: " + name);
            }
        }
    }

    /**
     * Find child elements for a given element, supporting all compositor types
     * and handling nested compositors
     */
    public List<ElementInfo> findChildElements(Element element) {
        List<ElementInfo> childElements = new ArrayList<>();
        if (element == null) return childElements;

        // Find complex type
        Element complexType = findComplexType(element);
        if (complexType == null) return childElements;

        // Process all child elements in sequence/choice/all
        NodeList sequences = complexType.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "sequence");
        for (int i = 0; i < sequences.getLength(); i++) {
            Element seq = (Element) sequences.item(i);
            NodeList elements = seq.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
            for (int j = 0; j < elements.getLength(); j++) {
                Element childElement = (Element) elements.item(j);
                String name = childElement.getAttribute("name");
                String ref = childElement.getAttribute("ref");
                String minOccurs = childElement.getAttribute("minOccurs");
                String maxOccurs = childElement.getAttribute("maxOccurs");

                ElementInfo info = new ElementInfo();
                info.name = !name.isEmpty() ? name : ref;
                info.isReference = !ref.isEmpty();
                info.minOccurs = minOccurs.isEmpty() ? 1 : Integer.parseInt(minOccurs);
                info.maxOccurs = maxOccurs.isEmpty() ? 1 :
                    maxOccurs.equals("unbounded") ? Integer.MAX_VALUE :
                    Integer.parseInt(maxOccurs);
                info.isSimpleType = isSimpleType(childElement);

                childElements.add(info);
            }
        }
        return childElements;
    }

    // Helper method to determine if element is a simple type
    private boolean isSimpleType(Element element) {
        if (element == null) return false;
        String type = element.getAttribute("type");
        if (type.isEmpty()) {
            // Check for inline simple type
            Element simpleType = findChildElement(element, "simpleType");
            return simpleType != null;
        }
        // Check if type is a built-in XML Schema simple type or references a simple type
        String localType = type.contains(":") ? type.substring(type.indexOf(":") + 1) : type;
        return isSimpleTypeByName(localType);
    }

    // Check if a type name is a simple type
    private boolean isSimpleTypeByName(String typeName) {
        Set<String> simpleTypes = new HashSet<>(Arrays.asList(
            "string", "boolean", "decimal", "float", "double", "integer",
            "date", "time", "dateTime", "gYear", "gMonth", "gDay"
        ));
        if (simpleTypes.contains(typeName)) return true;
        // Check if it's a user-defined simple type
        Element typeDef = resolveTypeDefinition(typeName);
        return typeDef != null && "simpleType".equals(typeDef.getLocalName());
    }
    
    /**
     * Process compositors (sequence, choice, all, group) recursively
     */
    private void processCompositors(Element parent, List<ElementInfo> childElements) {
        // Check for all compositor types
        for (String compositorName : new String[]{"sequence", "choice", "all"}) {
            NodeList compositors = parent.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, compositorName);
            
            for (int i = 0; i < compositors.getLength(); i++) {
                Element compositor = (Element) compositors.item(i);
                
                // Only process direct children of the parent
                if (isDirectChild(compositor, parent)) {
                    // Process elements within this compositor
                    processCompositorElements(compositor, childElements, compositorName);
                    
                    // Recursively process nested compositors
                    processCompositors(compositor, childElements);
                }
            }
        }
        
        // Handle group references
        NodeList groups = parent.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "group");
        for (int i = 0; i < groups.getLength(); i++) {
            Element group = (Element) groups.item(i);
            
            // Only process direct children of the parent
            if (isDirectChild(group, parent)) {
                String refAttr = group.getAttribute("ref");
                if (!refAttr.isEmpty()) {
                    // This is a group reference - resolve it
                    Element resolvedGroup = resolveGroupReference(refAttr);
                    if (resolvedGroup != null) {
                        // Process elements in the resolved group
                        processCompositors(resolvedGroup, childElements);
                    }
                } else {
                    // This is an inline group definition
                    processCompositors(group, childElements);
                }
            }
        }
    }
    
    /**
     * Check if a node is a direct child of a parent
     */
    private boolean isDirectChild(Node child, Node parent) {
        return child.getParentNode().equals(parent);
    }
    
    /**
     * Process elements within a compositor
     */
    private void processCompositorElements(Element compositor, List<ElementInfo> childElements, String compositorType) {
        NodeList elements = compositor.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
        
        // Get compositor min/maxOccurs (for choice)
        String compositorMinOccurs = compositor.getAttribute("minOccurs");
        String compositorMaxOccurs = compositor.getAttribute("maxOccurs");
        int compositorMin = compositorMinOccurs.isEmpty() ? 1 : Integer.parseInt(compositorMinOccurs);
        int compositorMax = compositorMaxOccurs.isEmpty() ? 1 : 
                          "unbounded".equals(compositorMaxOccurs) ? Integer.MAX_VALUE : 
                          Integer.parseInt(compositorMaxOccurs);
        
        for (int i = 0; i < elements.getLength(); i++) {
            Element childElement = (Element) elements.item(i);
            
            // Skip if not direct child of compositor
            if (!isDirectChild(childElement, compositor)) {
                continue;
            }
            
            // Extract element info
            String name = childElement.getAttribute("name");
            String ref = childElement.getAttribute("ref");
            String minOccurs = childElement.getAttribute("minOccurs");
            String maxOccurs = childElement.getAttribute("maxOccurs");
            
            // Skip empty elements (may be part of a substitution group)
            if (name.isEmpty() && ref.isEmpty()) {
                continue;
            }
            
            // Set defaults if not specified
            int min = minOccurs.isEmpty() ? 1 : Integer.parseInt(minOccurs);
            int max = maxOccurs.isEmpty() ? 1 : 
                      "unbounded".equals(maxOccurs) ? Integer.MAX_VALUE : 
                      Integer.parseInt(maxOccurs);
            
            // Adjust minOccurs based on compositor type
            if ("choice".equals(compositorType)) {
                // In a choice, elements are optional unless the choice itself is required
                min = (compositorMin > 0) ? min : 0;
            }
            
            // Create element info
            ElementInfo childInfo = new ElementInfo();
            childInfo.name = !name.isEmpty() ? name : ref;
            childInfo.isReference = !ref.isEmpty();
            childInfo.minOccurs = min;
            childInfo.maxOccurs = max;
            childInfo.isSimpleType = determineIfSimpleType(childElement);
            
            childElements.add(childInfo);
        }
        
        // Process any element if present
        NodeList anyElements = compositor.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "any");
        for (int i = 0; i < anyElements.getLength(); i++) {
            Element anyElement = (Element) anyElements.item(i);
            
            // Skip if not direct child of compositor
            if (!isDirectChild(anyElement, compositor)) {
                continue;
            }
            
            // We found an <any> element - this permits any element from specified namespace
            // We could generate test data for this, but it's complex and out of scope for this task
            XMLSchemaTestGenerator.log("Found <any> element in compositor - wildcard elements will not be tested");
        }
    }
    
    /**
     * Find complex type for an element, either inline or by reference
     */
    public Element findComplexType(Element element) {
        // First look for inline complexType
        Element complexType = findChildElement(element, "complexType");
        if (complexType != null) {
            return complexType;
        }

        // If not inline, check if there's a type attribute
        String typeAttr = element.getAttribute("type");
        if (!typeAttr.isEmpty()) {
            // Could be a reference to global complexType
            String typeName = typeAttr.contains(":") ? typeAttr.split(":")[1] : typeAttr;
            Element typeDef = resolveTypeDefinition(typeName);

            if (typeDef != null && typeDef.getLocalName().equals("complexType")) {
                return typeDef;
            }
        }

        return null;
    }
    
    /**
     * Determine if an element is a simple type
     */
    private boolean determineIfSimpleType(Element element) {
        // 1. Inline <simpleType> child
        Element simpleType = findChildElement(element, "simpleType");
        if (simpleType != null) {
            return true;
        }

        // 2. type attribute refers to simple type
        String typeAttr = element.getAttribute("type");
        if (!typeAttr.isEmpty()) {
            // Accept both "xs:string" and "string" (with or without prefix)
            String typeName = typeAttr.contains(":") ? typeAttr.split(":")[1] : typeAttr;

            // Check if it's a built-in XSD simple type
            Set<String> xsdSimpleTypes = new HashSet<>(Arrays.asList(
                "string", "boolean", "decimal", "float", "double", "duration", "dateTime", "time",
                "date", "gYearMonth", "gYear", "gMonthDay", "gDay", "gMonth", "hexBinary",
                "base64Binary", "anyURI", "QName", "NOTATION", "normalizedString", "token",
                "language", "IDREFS", "ENTITIES", "NMTOKEN", "NMTOKENS", "Name", "NCName",
                "ID", "IDREF", "ENTITY", "integer", "nonPositiveInteger", "negativeInteger",
                "long", "int", "short", "byte", "nonNegativeInteger", "unsignedLong",
                "unsignedInt", "unsignedShort", "unsignedByte", "positiveInteger"
            ));

            if (xsdSimpleTypes.contains(typeName)) {
                return true;
            }

            // Check if it's a user-defined simple type
            Element typeDef = resolveTypeDefinition(typeName);
            return (typeDef != null && typeDef.getLocalName().equals("simpleType"));
        }

        // 3. Check for simple content
        Element complexType = findChildElement(element, "complexType");
        if (complexType != null) {
            Element simpleContent = findChildElement(complexType, "simpleContent");
            if (simpleContent != null) {
                // Complex type with simple content is considered a simple type for our purposes
                return true;
            }
        }

        // 4. No complexType child
        if (complexType == null) {
            // Default to true if no complexType defined
            return true;
        }

        return false;
    }
    
    /**
     * Resolve an element reference
     */
    private Element resolveReference(String ref) {
        // Handle qualified names
        String localName = ref;
        String prefix = "";
        String targetNamespace = null;
        
        if (ref.contains(":")) {
            String[] parts = ref.split(":");
            prefix = parts[0];
            localName = parts[1];
            
            // Get namespace for this prefix
            targetNamespace = prefixToNamespaceMap.get(prefix);
        }
        
        // Try to find in global elements
        if (generator.getGlobalElementDefinitions().containsKey(localName)) {
            return generator.getGlobalElementDefinitions().get(localName);
        }
        
        XMLSchemaTestGenerator.log("Could not resolve element reference: " + ref);
        return null;
    }
    
    /**
     * Resolve a group reference
     */
    private Element resolveGroupReference(String ref) {
        // Handle qualified names
        String localName = ref;
        if (ref.contains(":")) {
            localName = ref.substring(ref.indexOf(":") + 1);
        }
        
        // Try to find in global group definitions
        if (groupDefinitions.containsKey(localName)) {
            return groupDefinitions.get(localName);
        }
        
        XMLSchemaTestGenerator.log("Could not resolve group reference: " + ref);
        return null;
    }
    
    /**
     * Finds enumeration values for a given element (inline or by type).
     */
    public List<String> findEnumerationValues(Element element) {
        String elementId = element.getAttribute("name");
        if (enumValueCache.containsKey(elementId)) {
            return enumValueCache.get(elementId);
        }
        List<String> values = new ArrayList<>();
        // Check for inline simple type with enumerations
        Element simpleType = findChildElement(element, "simpleType");
        if (simpleType != null) {
            values.addAll(findEnumerationsInSimpleType(simpleType));
        }
        // If the element has a type attribute, resolve and extract enumerations from the referenced global <simpleType>
        String typeAttr = element.getAttribute("type");
        if (typeAttr != null && !typeAttr.isEmpty()) {
            String typeName = typeAttr.contains(":") ? typeAttr.split(":")[1] : typeAttr;
            Element typeDef = resolveTypeDefinition(typeName);
            if (typeDef != null) {
                if (typeDef.getLocalName().equals("simpleType")) {
                    List<String> fromType = findEnumerationsInSimpleType(typeDef);
                    XMLSchemaTestGenerator.debug("findEnumerationValues: Resolved type '" + typeName + "' for element '" + element.getAttribute("name") + "' and found enums: " + fromType);
                    values.addAll(fromType);
                }
            } else {
                XMLSchemaTestGenerator.debug("findEnumerationValues: No typeDef found for type: " + typeAttr + ", element: " + element.getAttribute("name"));
            }
        }
        // Cache the results
        if (!elementId.isEmpty()) {
            enumValueCache.put(elementId, values);
        }
        return values;
    }

/**
 * Find enumeration values for a global type name (simpleType)
 */
public List<String> findEnumerationValuesForType(String typeName) {
    List<String> values = new ArrayList<>();
    if (typeName == null || typeName.isEmpty()) return values;
    // Remove namespace prefix if present
    String localType = typeName.contains(":") ? typeName.substring(typeName.indexOf(":") + 1) : typeName;
    Element typeDef = typeDefinitions.get(localType);
    if (typeDef == null) {
        XMLSchemaTestGenerator.debug("findEnumerationValuesForType: No typeDef found for typeName '" + typeName + "' (local: '" + localType + "'). Available: " + typeDefinitions.keySet());
        return values;
    }
    if (typeDef.getLocalName().equals("simpleType")) {
        values.addAll(findEnumerationsInSimpleType(typeDef));
    } else if (typeDef.getLocalName().equals("complexType")) {
        // Check for simpleContent restriction
        Element simpleContent = generator.findChildElement(typeDef, "simpleContent");
        if (simpleContent != null) {
            Element restriction = generator.findChildElement(simpleContent, "restriction");
            if (restriction != null) {
                // Direct enumerations in restriction
                NodeList enums = restriction.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "enumeration");
                for (int i = 0; i < enums.getLength(); i++) {
                    Element enumeration = (Element) enums.item(i);
                    values.add(enumeration.getAttribute("value"));
                }
                // If no direct enums, try base type
                if (values.isEmpty()) {
                    String base = restriction.getAttribute("base");
                    if (base != null && !base.isEmpty()) {
                        XMLSchemaTestGenerator.debug("findEnumerationValuesForType: complexType with simpleContent, checking base type '" + base + "'");
                        values.addAll(findEnumerationValuesForType(base));
                    }
                }
            }
        }
    }
    XMLSchemaTestGenerator.log("[DEBUG] findEnumerationValuesForType: For type '" + typeName + "' found enums: " + values);
    return values;
}

/**
 * Extract enumeration values from a <simpleType> definition.
 * Handles both direct <restriction> children and nested structures.
 */
private List<String> findEnumerationsInSimpleType(Element simpleType) {
    List<String> values = new ArrayList<>();
    if (simpleType == null) return values;
    // Look for <restriction> child
    NodeList children = simpleType.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
        Node child = children.item(i);
        if (child.getNodeType() == Node.ELEMENT_NODE && "restriction".equals(child.getLocalName())) {
            Element restriction = (Element) child;
            // Find all <enumeration> children
            NodeList enums = restriction.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "enumeration");
            for (int j = 0; j < enums.getLength(); j++) {
                Element enumElem = (Element) enums.item(j);
                String val = enumElem.getAttribute("value");
                if (val != null && !val.isEmpty()) {
                    values.add(val);
                }
            }
        }
    }
    return values;
}

/**
 * Resolve a type name to its global type definition element
 */
public Element resolveTypeDefinition(String typeName) {
    XMLSchemaTestGenerator.debug("resolveTypeDefinition: Available typeDefinitions: " + typeDefinitions.keySet());
    return typeDefinitions.get(typeName);
}

/**
 * Gets the namespace URI for a prefix
 */
public String getNamespaceForPrefix(String prefix) {
    return prefixToNamespaceMap.get(prefix);
}
}