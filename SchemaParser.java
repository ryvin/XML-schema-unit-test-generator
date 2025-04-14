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
import java.util.logging.Logger;
import java.util.logging.Level;

public class SchemaParser {
    
    private static final Logger logger = Logger.getLogger(SchemaParser.class.getName());
    private XMLSchemaTestGenerator generator;
    
    // Map to store all global type definitions (simpleType and complexType) by name
    public static Map<String, Element> typeDefinitions = new HashMap<>();
    
    // Map to track resolved references to avoid circular reference issues
    private Set<String> resolvedReferences = new HashSet<>();
    
    // Map to store namespace prefixes used in schema for resolving QNames
    private Map<String, String> prefixToNamespaceMap = new HashMap<>();
    
    // Map to store global group definitions
    private Map<String, Element> groupDefinitions = new HashMap<>();
    
    // Map to store substitution groups
    private Map<String, List<Element>> substitutionGroups = new HashMap<>();
    
    public SchemaParser(XMLSchemaTestGenerator generator) {
        this.generator = generator;
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
                        logger.log(Level.WARNING, "Could not process included schema: {0}", fullPath);
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
                                    logger.log(Level.FINE, "Found prefix {0} for namespace {1}", 
                                        new Object[]{entry.getKey(), namespace});
                                    break;
                                }
                            }
                        }
                        
                        // Recursively process includes/imports
                        collectIncludedSchemas(importedDoc, fullPath, processedSchemas, schemaDocuments);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Could not process imported schema: {0}", fullPath);
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
                logger.log(Level.FINE, "Added namespace prefix mapping: {0} -> {1}", 
                    new Object[]{prefix, value});
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
        for (int i = 0; i <simpleTypes.getLength(); i++) {
            Element simpleType = (Element) simpleTypes.item(i);
            Node parent = simpleType.getParentNode();
            if (parent != null && (parent.getLocalName().equals("schema") || parent.getNodeName().equals("xs:schema"))) {
                String name = simpleType.getAttribute("name");
                if (!name.isEmpty()) {
                    typeDefinitions.put(name, simpleType);
                    logger.info("[LOG] Registered global simpleType: " + name);
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
                    logger.info("[LOG] Registered global complexType: " + name);
                }
            }
        }
        // --- END PATCH ---
        
        // Process global elements
        NodeList elements = schemaDoc.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            Node parent = element.getParentNode();
            
            // Check if this is a global element (direct child of schema)
            if (parent != null &&
                (parent.getLocalName().equals("schema") || parent.getNodeName().equals("xs:schema"))) {
                String name = element.getAttribute("name");
                if (!name.isEmpty()) {
                    generator.getGlobalElementDefinitions().put(name, element);
                    
                    // Check for substitution group
                    String substitutionGroup = element.getAttribute("substitutionGroup");
                    if (!substitutionGroup.isEmpty()) {
                        // Extract the local name if it's a qualified name
                        String localName = substitutionGroup;
                        if (substitutionGroup.contains(":")) {
                            localName = substitutionGroup.substring(substitutionGroup.indexOf(":") + 1);
                        }
                        
                        // Add to substitution group map
                        if (!substitutionGroups.containsKey(localName)) {
                            substitutionGroups.put(localName, new ArrayList<>());
                        }
                        substitutionGroups.get(localName).add(element);
                        logger.log(Level.FINE, "Added element {0} to substitution group {1}", 
                            new Object[]{name, localName});
                    }
                    
                    // Store child elements info
                    List<ElementInfo> childElements = findChildElements(element);
                    if (!childElements.isEmpty()) {
                        generator.getGlobalElementsMap().put(name, childElements);
                    }
                }
            }
        }
        
        // Index all global simpleType and complexType definitions
        indexGlobalTypeDefinitions(schemaDoc);
        
        // Index all global group definitions
        indexGlobalGroupDefinitions(schemaDoc);
    }
    
    /**
     * Index all global type definitions for later reference
     */
    private void indexGlobalTypeDefinitions(Document schemaDoc) {
        // Process simpleTypes
        NodeList simpleTypes = schemaDoc.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element typeElem = (Element) simpleTypes.item(i);
            Node parent = typeElem.getParentNode();
            if (parent != null &&
                (parent.getLocalName().equals("schema") || parent.getNodeName().equals("xs:schema"))) {
                String name = typeElem.getAttribute("name");
                if (!name.isEmpty()) {
                    typeDefinitions.put(name, typeElem);
                    logger.log(Level.FINE, "Added global simpleType definition: {0}", name);
                }
            }
        }
        
        // Process complexTypes
        NodeList complexTypes = schemaDoc.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element typeElem = (Element) complexTypes.item(i);
            Node parent = typeElem.getParentNode();
            if (parent != null &&
                (parent.getLocalName().equals("schema") || parent.getNodeName().equals("xs:schema"))) {
                String name = typeElem.getAttribute("name");
                if (!name.isEmpty()) {
                    typeDefinitions.put(name, typeElem);
                    logger.log(Level.FINE, "Added global complexType definition: {0}", name);
                }
            }
        }
    }
    
    /**
     * Index all global group definitions for later reference
     */
    private void indexGlobalGroupDefinitions(Document schemaDoc) {
        NodeList groups = schemaDoc.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "group");
        for (int i = 0; i < groups.getLength(); i++) {
            Element groupElem = (Element) groups.item(i);
            Node parent = groupElem.getParentNode();
            if (parent != null &&
                (parent.getLocalName().equals("schema") || parent.getNodeName().equals("xs:schema"))) {
                String name = groupElem.getAttribute("name");
                if (!name.isEmpty()) {
                    groupDefinitions.put(name, groupElem);
                    logger.log(Level.FINE, "Added global group definition: {0}", name);
                }
            }
        }
    }
    
    /**
     * Find child elements for a given element, supporting all compositor types
     * and handling nested compositors
     */
    public List<ElementInfo> findChildElements(Element element) {
        List<ElementInfo> childElements = new ArrayList<>();
        
        // Remember element name to avoid circular references
        String elementId = element.getAttribute("name");
        if (elementId.isEmpty()) {
            elementId = element.getAttribute("ref");
        }
        
        // Resolve references if this is a reference element
        Element resolvedElement = element;
        String refAttr = element.getAttribute("ref");
        if (!refAttr.isEmpty()) {
            // This is a reference - resolve it
            resolvedElement = resolveReference(refAttr);
            
            if (resolvedElement != null) {
                // We resolved the reference, now process the resolved element
                elementId = resolvedElement.getAttribute("name");
                
                // Avoid circular references
                if (resolvedReferences.contains(elementId)) {
                    logger.log(Level.FINE, "Detected circular reference for element: {0}", elementId);
                    return childElements;
                }
                
                resolvedReferences.add(elementId);
            } else {
                // Failed to resolve reference
                return childElements;
            }
        }
        
        // Check for complex type
        Element complexType = findComplexType(resolvedElement);
        if (complexType != null) {
            // Process all compositors recursively (sequence, choice, all, group)
            processCompositors(complexType, childElements);
        }
        
        // Add elements from any substitution groups
        if (!elementId.isEmpty() && substitutionGroups.containsKey(elementId)) {
            List<Element> substitutes = substitutionGroups.get(elementId);
            for (Element substitute : substitutes) {
                String substituteName = substitute.getAttribute("name");
                ElementInfo substitutionInfo = new ElementInfo();
                substitutionInfo.name = substituteName;
                substitutionInfo.isReference = false;
                substitutionInfo.minOccurs = 0; // Substitutions are optional
                substitutionInfo.maxOccurs = 1;
                substitutionInfo.isSimpleType = determineIfSimpleType(substitute);
                
                childElements.add(substitutionInfo);
                logger.log(Level.FINE, "Added substitution element: {0} for {1}", 
                    new Object[]{substituteName, elementId});
            }
        }
        
        // Clear resolved reference once we're done with this element
        resolvedReferences.remove(elementId);
        
        return childElements;
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
            logger.log(Level.INFO, "Found <any> element in compositor - wildcard elements will not be tested");
        }
    }
    
    /**
     * Find complex type for an element, either inline or by reference
     */
    private Element findComplexType(Element element) {
        // First look for inline complexType
        Element complexType = generator.findChildElement(element, "complexType");
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
        Element simpleType = generator.findChildElement(element, "simpleType");
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
        Element complexType = generator.findChildElement(element, "complexType");
        if (complexType != null) {
            Element simpleContent = generator.findChildElement(complexType, "simpleContent");
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
        
        logger.log(Level.WARNING, "Could not resolve element reference: {0}", ref);
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
        
        logger.log(Level.WARNING, "Could not resolve group reference: {0}", ref);
        return null;
    }
    
    /**
     * Find enumeration values for an element or attribute
     */
    public List<String> findEnumerationValues(Element element) {
        // Check cache first
        String elementId = element.getAttribute("name");
        if (generator.getEnumValueCache().containsKey(elementId)) {
            return generator.getEnumValueCache().get(elementId);
        }
        
        List<String> values = new ArrayList<>();
        
        // Check for inline simple type with enumerations
        Element simpleType = generator.findChildElement(element, "simpleType");
        if (simpleType != null) {
            values.addAll(findEnumerationsInSimpleType(simpleType));
        }

        // --- PATCH: If the element is local (not global), and has a type attribute, resolve and extract enumerations from the referenced global <simpleType> ---
        String typeAttr = element.getAttribute("type");
        if (typeAttr != null && !typeAttr.isEmpty()) {
            String typeName = typeAttr.contains(":") ? typeAttr.split(":")[1] : typeAttr;
            Element typeDef = resolveTypeDefinition(typeName);
            if (typeDef != null) {
                if (typeDef.getLocalName().equals("simpleType")) {
                    List<String> fromType = findEnumerationsInSimpleType(typeDef);
                    System.out.println("[DEBUG] findEnumerationValues: Resolved type '" + typeName + "' for element '" + element.getAttribute("name") + "' and found enums: " + fromType);
                    values.addAll(fromType);
                }
            } else {
                System.out.println("[DEBUG] findEnumerationValues: No typeDef found for type: " + typeAttr + ", element: " + element.getAttribute("name"));
            }
        }

        // Cache the results
        if (!elementId.isEmpty()) {
            generator.getEnumValueCache().put(elementId, values);
        }
        return values;
    }
    
    /**
     * Find enumeration values in a simple type definition
     */
    private List<String> findEnumerationsInSimpleType(Element simpleType) {
        List<String> values = new ArrayList<>();
        
        // Look for direct restriction
        Element restriction = generator.findChildElement(simpleType, "restriction");
        if (restriction != null) {
            NodeList enumerations = restriction.getElementsByTagNameNS(
                    XMLConstants.W3C_XML_SCHEMA_NS_URI, "enumeration");
            
            for (int i = 0; i < enumerations.getLength(); i++) {
                Element enumeration = (Element) enumerations.item(i);
                values.add(enumeration.getAttribute("value"));
            }
        }
        
        // Look for union
        Element union = generator.findChildElement(simpleType, "union");
        if (union != null) {
            // Process member types
            String memberTypes = union.getAttribute("memberTypes");
            if (!memberTypes.isEmpty()) {
                String[] types = memberTypes.split("\\s+");
                for (String type : types) {
                    // Remove prefix if present
                    String typeName = type.contains(":") ? type.split(":")[1] : type;
                    Element typeDef = resolveTypeDefinition(typeName);
                    if (typeDef != null && typeDef.getLocalName().equals("simpleType")) {
                        values.addAll(findEnumerationsInSimpleType(typeDef));
                    }
                }
            }
            
            // Process inline simple types
            NodeList inlineTypes = union.getElementsByTagNameNS(
                    XMLConstants.W3C_XML_SCHEMA_NS_URI, "simpleType");
            
            for (int i = 0; i < inlineTypes.getLength(); i++) {
                Element inlineType = (Element) inlineTypes.item(i);
                values.addAll(findEnumerationsInSimpleType(inlineType));
            }
        }
        
        // Look for list
        Element list = generator.findChildElement(simpleType, "list");
        if (list != null) {
            // Lists don't have enumerations directly, but their item type might
            String itemType = list.getAttribute("itemType");
            if (!itemType.isEmpty()) {
                // Remove prefix if present
                String typeName = itemType.contains(":") ? itemType.split(":")[1] : itemType;
                Element typeDef = resolveTypeDefinition(typeName);
                if (typeDef != null && typeDef.getLocalName().equals("simpleType")) {
                    values.addAll(findEnumerationsInSimpleType(typeDef));
                }
            }
            
            // Check for inline simple type
            Element inlineType = generator.findChildElement(list, "simpleType");
            if (inlineType != null) {
                values.addAll(findEnumerationsInSimpleType(inlineType));
            }
        }
        
        return values;
    }

    /**
     * Resolve a type name to its global type definition element
     */
    public Element resolveTypeDefinition(String typeName) {
        logger.info("[LOG] resolveTypeDefinition: Available typeDefinitions: " + typeDefinitions.keySet());
        return typeDefinitions.get(typeName);
    }
    
    /**
     * Gets the namespace URI for a prefix
     */
    public String getNamespaceForPrefix(String prefix) {
        return prefixToNamespaceMap.get(prefix);
    }
}