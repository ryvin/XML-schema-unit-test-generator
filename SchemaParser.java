import java.io.File;
import java.util.*;
import javax.xml.XMLConstants;
import org.w3c.dom.*;

/**
 * Class for parsing XML Schema files and extracting information
 */
public class SchemaParser {
    
    private XMLSchemaTestGenerator generator;
    
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
    }
    
    /**
     * Find child elements for a given element
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
            Element restriction = generator.findChildElement(simpleType, "restriction");
            if (restriction != null) {
                NodeList enumerations = restriction.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "enumeration");
                for (int i = 0; i < enumerations.getLength(); i++) {
                    Element enumeration = (Element) enumerations.item(i);
                    values.add(enumeration.getAttribute("value"));
                }
            }
        }
        
        // Check for type attribute with enumeration
        String typeName = element.getAttribute("type");
        if (!typeName.isEmpty()) {
            // This is simplified - a full implementation would need to resolve type references
            // to find their enumerations if they're simple types with restrictions
        }
        
        // Cache the results
        if (!elementId.isEmpty()) {
            generator.getEnumValueCache().put(elementId, values);
        }
        return values;
    }
}