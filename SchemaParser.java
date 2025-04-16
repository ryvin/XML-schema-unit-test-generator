import java.io.File;
import java.util.*;
import javax.xml.XMLConstants;
import org.w3c.dom.*;

/**
 * Class for parsing XML Schema files and extracting information
 */
/**
 * Parses XML Schema files and provides utilities for extracting schema structure and constraints.
 */
public class SchemaParser {
    /**
     * Find child element by local name (delegates to generator)
     */
    /**
     * Finds the first child element with the given local name.
     * @param parent The parent element.
     * @param localName The local name of the child to find.
     * @return The child element, or null if not found.
     */
    public Element findChildElement(Element parent, String localName) {
        return generator.findChildElement(parent, localName);
    }

    
    private XMLSchemaTestGenerator generator;
    // Map to store all global type definitions (simpleType and complexType) by name
    private Map<String, Element> typeDefinitions = new HashMap<>();
    
    /**
     * Constructs the SchemaParser with a reference to the main generator.
     * @param generator The main XMLSchemaTestGenerator instance.
     */
    public SchemaParser(XMLSchemaTestGenerator generator) {
        this.generator = generator;
    }
    
    /**
     * Collect included and imported schema documents
     */
    /**
     * Recursively collects included and imported schemas.
     * @param schemaDoc The root schema document.
     * @param baseSchemaFile Path to the base schema file.
     * @param processedSchemas Set of already processed schema paths.
     * @param schemaDocuments List to store all loaded schema documents.
     * @throws Exception if any schema cannot be loaded.
     */
    public void collectIncludedSchemas(Document schemaDoc, String baseSchemaFile, 
                                      Set<String> processedSchemas, List<Document> schemaDocuments) throws Exception {
        File baseFile = new File(baseSchemaFile);
        String basePath = baseFile.getParent();
        if (basePath == null) {
            basePath = ".";
        }
        
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
                        
                        // Recursively process includes/imports
                        collectIncludedSchemas(includedDoc, fullPath, processedSchemas, schemaDocuments);
                    } catch (Exception e) {
                        System.err.println("Warning: Could not process included schema: " + fullPath);
                    }
                }
            }
        }
        
        // Process imports
        NodeList imports = schemaDoc.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "import");
        for (int i = 0; i < imports.getLength(); i++) {
            Element importElem = (Element) imports.item(i);
            String schemaLocation = importElem.getAttribute("schemaLocation");
            
            if (!schemaLocation.isEmpty()) {
                String fullPath = basePath + File.separator + schemaLocation;
                
                if (!processedSchemas.contains(fullPath)) {
                    try {
                        Document importedDoc = generator.parseXmlFile(fullPath);
                        schemaDocuments.add(importedDoc);
                        processedSchemas.add(fullPath);
                        
                        // Extract namespace information from imported schema
                        generator.extractNamespaces(importedDoc.getDocumentElement());
                        
                        // Recursively process includes/imports
                        collectIncludedSchemas(importedDoc, fullPath, processedSchemas, schemaDocuments);
                    } catch (Exception e) {
                        System.err.println("Warning: Could not process imported schema: " + fullPath);
                    }
                }
            }
        }
    }
    
    /**
     * Find all global elements in the schema for reference resolution
     */
    /**
     * Finds and indexes all global elements and type definitions in the schema.
     * @param schemaDoc The schema document.
     */
    public void findAllGlobalElements(Document schemaDoc) {
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
                    
                    // Store child elements info
                    List<ElementInfo> childElements = findChildElements(element);
                    if (!childElements.isEmpty()) {
                        generator.getGlobalElementsMap().put(name, childElements);
                    }
                }
            }
        }
        // Index all global simpleType and complexType definitions
        NodeList simpleTypes = schemaDoc.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element typeElem = (Element) simpleTypes.item(i);
            Node parent = typeElem.getParentNode();
            if (parent != null &&
                (parent.getLocalName().equals("schema") || parent.getNodeName().equals("xs:schema"))) {
                String name = typeElem.getAttribute("name");
                if (!name.isEmpty()) {
                    typeDefinitions.put(name, typeElem);
                }
            }
        }
        NodeList complexTypes = schemaDoc.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element typeElem = (Element) complexTypes.item(i);
            Node parent = typeElem.getParentNode();
            if (parent != null &&
                (parent.getLocalName().equals("schema") || parent.getNodeName().equals("xs:schema"))) {
                String name = typeElem.getAttribute("name");
                if (!name.isEmpty()) {
                    typeDefinitions.put(name, typeElem);
                }
            }
        }
    }
    
    /**
     * Find child elements for a given element
     */
    /**
     * Finds all child elements for a given element (handles complexType/sequence/choice/all).
     * @param element The parent schema element.
     * @return List of child element info.
     */
    public List<ElementInfo> findChildElements(Element element) {
        List<ElementInfo> childElements = new ArrayList<>();
        
        // Check for complex type
        Element complexType = generator.findChildElement(element, "complexType");
        if (complexType != null) {
            // Look for sequence, choice, or all compositors
            for (String compositor : new String[]{"sequence", "choice", "all"}) {
                Element compositorElement = generator.findChildElement(complexType, compositor);
                if (compositorElement != null) {
                    // Find elements in this compositor
                    NodeList elements = compositorElement.getElementsByTagNameNS(
                            XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                    
                    for (int i = 0; i < elements.getLength(); i++) {
                        Element childElement = (Element) elements.item(i);
                        
                        // Skip if not direct child of compositor
                        if (!childElement.getParentNode().equals(compositorElement)) {
                            continue;
                        }
                        
                        // Extract element info
                        String name = childElement.getAttribute("name");
                        String ref = childElement.getAttribute("ref");
                        String minOccurs = childElement.getAttribute("minOccurs");
                        String maxOccurs = childElement.getAttribute("maxOccurs");
                        
                        // Set defaults if not specified
                        int min = minOccurs.isEmpty() ? 1 : Integer.parseInt(minOccurs);
                        int max = maxOccurs.isEmpty() ? 1 : 
                                  "unbounded".equals(maxOccurs) ? Integer.MAX_VALUE : 
                                  Integer.parseInt(maxOccurs);
                        
                        // Create element info
                        ElementInfo childInfo = new ElementInfo();
                        childInfo.name = !name.isEmpty() ? name : ref;
                        childInfo.isReference = !ref.isEmpty();
                        childInfo.minOccurs = min;
                        childInfo.maxOccurs = max;

                        // Determine if this is a simple type
                        // 1. Inline <simpleType> child
                        Element simpleType = generator.findChildElement(childElement, "simpleType");
                        if (simpleType != null) {
                            childInfo.isSimpleType = true;
                        } else {
                            // 2. type attribute refers to built-in XSD simple type
                            String typeAttr = childElement.getAttribute("type");
                            if (!typeAttr.isEmpty()) {
                                // Accept both "xs:string" and "string" (with or without prefix)
                                String typeName = typeAttr.contains(":") ? typeAttr.split(":")[1] : typeAttr;
                                Set<String> xsdSimpleTypes = new HashSet<>(Arrays.asList(
                                    "string", "boolean", "decimal", "float", "double", "duration", "dateTime", "time",
                                    "date", "gYearMonth", "gYear", "gMonthDay", "gDay", "gMonth", "hexBinary",
                                    "base64Binary", "anyURI", "QName", "NOTATION", "normalizedString", "token",
                                    "language", "IDREFS", "ENTITIES", "NMTOKEN", "NMTOKENS", "Name", "NCName",
                                    "ID", "IDREF", "ENTITY", "integer", "nonPositiveInteger", "negativeInteger",
                                    "long", "int", "short", "byte", "nonNegativeInteger", "unsignedLong",
                                    "unsignedInt", "unsignedShort", "unsignedByte", "positiveInteger"
                                ));
                                childInfo.isSimpleType = xsdSimpleTypes.contains(typeName);
                            } else {
                                childInfo.isSimpleType = false;
                            }
                        }
                        
                        childElements.add(childInfo);
                    }
                    
                    break; // Only process the first compositor found
                }
            }
        }
        
        return childElements;
    }
    
    /**
     * Find enumeration values for an element or attribute
     */
    /**
     * Finds all enumeration values for an element or attribute.
     * @param element The schema element or attribute.
     * @return List of valid enumeration values.
     */
    public List<String> findEnumerationValues(Element element) {
        
        List<String> values = new ArrayList<>();
        
        // Check for inline simple type with enumerations
        Element simpleType = generator.findChildElement(element, "simpleType");
        if (simpleType != null) {
            Element restriction = generator.findChildElement(simpleType, "restriction");
            if (restriction != null) {
                NodeList enumerations = restriction.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "enumeration");
                for (int i = 0; i < enumerations.getLength(); i++) {
                    Element enumeration = (Element) enumerations.item(i);
                    values.add(enumeration.getAttribute("value"));
                }
            }
        }

        // If no inline enumerations, check for type attribute and resolve type
        if (values.isEmpty()) {
            String typeName = element.getAttribute("type");
            if (!typeName.isEmpty()) {
                // Remove prefix if present
                String resolvedTypeName = typeName.contains(":") ? typeName.split(":")[1] : typeName;
                Element typeDef = resolveTypeDefinition(resolvedTypeName);
                if (typeDef != null) {
                    // Only handle simpleType for enumerations
                    if (typeDef.getLocalName().equals("simpleType")) {
                        Element restriction = generator.findChildElement(typeDef, "restriction");
                        if (restriction != null) {
                            NodeList enumerations = restriction.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "enumeration");
                            for (int i = 0; i < enumerations.getLength(); i++) {
                                Element enumeration = (Element) enumerations.item(i);
                                values.add(enumeration.getAttribute("value"));
                            }
                        }
                    }
                }
            }
        }
        
        return values;
    }

    // Resolve a type name to its global type definition element
    /**
     * Resolves a type name to its global type definition element.
     * @param typeName The name of the type.
     * @return The type definition element, or null if not found.
     */
    public Element resolveTypeDefinition(String typeName) {
        if (typeDefinitions.containsKey(typeName)) {
            return typeDefinitions.get(typeName);
        }
        return null;
    }
}