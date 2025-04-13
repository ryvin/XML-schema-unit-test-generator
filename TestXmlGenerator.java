import java.util.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Class for generating XML test files
 */
public class TestXmlGenerator {
    
    private XMLSchemaTestGenerator generator;
    private SchemaParser schemaParser;
    
    public TestXmlGenerator(XMLSchemaTestGenerator generator, SchemaParser schemaParser) {
        this.generator = generator;
        this.schemaParser = schemaParser;
    }
    
    /**
     * Generate XML for testing cardinality constraints
     */
    public String generateTestXml(String parentName, List<ElementInfo> allChildElements, 
                                 String targetChildName, int occurrences, boolean isReference, String namespace) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        
        // Extract prefix and local name
        String parentPrefix = generator.getDefaultNamespacePrefix();
        
        // Add root element with namespace declarations
        if (namespace != null && !namespace.isEmpty()) {
            xml.append("<").append(parentPrefix).append(":").append(parentName);
            
            // Add all namespace declarations
            for (Map.Entry<String, String> entry : generator.getNamespaceMap().entrySet()) {
                xml.append(" xmlns:").append(entry.getKey())
                   .append("=\"").append(entry.getValue()).append("\"");
            }
            
            xml.append(">\n");
        } else {
            xml.append("<").append(parentName).append(">\n");
        }
        
        // Add child elements in the required sequence
        for (ElementInfo childInfo : allChildElements) {
            String childName = childInfo.name;
            
            // Skip elements we're not testing in this case and maintain the right sequence
            if (!childName.equals(targetChildName)) {
                // Add a single instance of non-target elements to satisfy minimum requirements
                if (childInfo.minOccurs > 0) {
                    addCompleteElementInstance(xml, childName, childInfo.isReference, 1, namespace);
                }
                continue;
            }
            
            // For the target element, add the specified number of occurrences
            addCompleteElementInstance(xml, childName, isReference, occurrences, namespace);
        }
        
        // Close parent element
        if (namespace != null && !namespace.isEmpty()) {
            xml.append("</").append(parentPrefix).append(":").append(parentName).append(">\n");
        } else {
            xml.append("</").append(parentName).append(">\n");
        }
        
        return xml.toString();
    }
    
    /**
     * Add complete element instances with appropriate structure based on element name
     */
    public void addCompleteElementInstance(StringBuilder xml, String elementName, boolean isReference, 
                                           int count, String namespace) {
        // Extract prefix and local name
        String prefix = generator.getDefaultNamespacePrefix();
        String localName = elementName;
        if (elementName.contains(":")) {
            String[] parts = elementName.split(":");
            prefix = parts[0];
            localName = parts[1];
        }
        
        // Get namespace URI for this element
        String elementNamespace = namespace;
        if (!prefix.isEmpty() && generator.getNamespaceMap().containsKey(prefix)) {
            elementNamespace = generator.getNamespaceMap().get(prefix);
        }
        
        // Check if this is a global element
        boolean isGlobalElement = generator.getGlobalElementDefinitions().containsKey(localName);
        
        for (int i = 0; i < count; i++) {
            // Get the schema Element for this element
            Element schemaElement = generator.getGlobalElementDefinitions().get(localName);
            if (schemaElement == null) {
                // Try to find in child lists
                for (Element e : generator.getGlobalElementDefinitions().values()) {
                    if (e.getAttribute("name").equals(localName)) {
                        schemaElement = e;
                        break;
                    }
                }
            }

            // Prepare attributes string (handle enumerations for attributes if needed)
            StringBuilder attrBuilder = new StringBuilder();
            if (schemaElement != null) {
                // Handle attributes
                Element complexType = generator.findChildElement(schemaElement, "complexType");
                if (complexType != null) {
                    NodeList attributes = complexType.getElementsByTagNameNS(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI, "attribute");
                    for (int a = 0; a < attributes.getLength(); a++) {
                        Element attrElem = (Element) attributes.item(a);
                        String attrName = attrElem.getAttribute("name");
                        List<String> attrEnums = schemaParser.findEnumerationValues(attrElem);
                        String attrValue;
                        if (!attrEnums.isEmpty()) {
                            attrValue = attrEnums.get(0);
                        } else {
                            // Use a generic value based on type
                            String type = attrElem.getAttribute("type");
                            if (type.endsWith("string")) {
                                attrValue = "SampleString";
                            } else if (type.endsWith("gYear")) {
                                attrValue = "2020";
                            } else if (type.endsWith("int") || type.endsWith("integer")) {
                                attrValue = "1";
                            } else {
                                attrValue = "SampleValue";
                            }
                        }
                        attrBuilder.append(" ").append(attrName).append("=\"").append(attrValue).append("\"");
                    }
                }
            }

            // Determine if this element is a simple type
            boolean isSimpleType = false;
            // Use SchemaParser to get children for this element (handles inline complex types)
            List<ElementInfo> children = new ArrayList<>();
            if (schemaElement != null) {
                children = schemaParser.findChildElements(schemaElement);
            }

            // Try to find ElementInfo for this element
            ElementInfo info = null;
            if (generator.getGlobalElementDefinitions().containsKey(localName)) {
                if (children != null && !children.isEmpty()) {
                    isSimpleType = false;
                } else {
                    for (List<ElementInfo> childList : generator.getGlobalElementsMap().values()) {
                        for (ElementInfo e : childList) {
                            if (e.name.equals(localName)) {
                                info = e;
                                break;
                            }
                        }
                        if (info != null) break;
                    }
                    if (info != null) {
                        isSimpleType = info.isSimpleType;
                    } else {
                        isSimpleType = false;
                    }
                }
            } else {
                for (List<ElementInfo> childList : generator.getGlobalElementsMap().values()) {
                    for (ElementInfo e : childList) {
                        if (e.name.equals(localName)) {
                            info = e;
                            break;
                        }
                    }
                    if (info != null) break;
                }
                if (info != null) {
                    isSimpleType = info.isSimpleType;
                }
            }

            // Add opening tag with attributes if any
            xml.append("  <").append(prefix).append(":").append(localName).append(attrBuilder).append(">\n");

            if (children != null && !children.isEmpty()) {
                // Complex type: always add all required children recursively, no text content
                for (ElementInfo child : children) {
                    int childCount = Math.max(child.minOccurs, 1); // Always at least 1
                    addCompleteElementInstance(xml, child.name, child.isReference, childCount, elementNamespace);
                }
            } else if (isSimpleType) {
                // Only add text content for simple types with no children
                String value = null;
                // Use enumeration value if present
                if (schemaElement != null) {
                    List<String> enums = schemaParser.findEnumerationValues(schemaElement);
                    if (!enums.isEmpty()) {
                        value = enums.get(0);
                    }
                }
                if (value == null && schemaElement != null) {
                    // Use type to generate a value
                    String type = schemaElement.getAttribute("type");
                    if (type.endsWith("string")) {
                        value = "SampleString";
                    } else if (type.endsWith("gYear")) {
                        value = "2020";
                    } else if (type.endsWith("int") || type.endsWith("integer")) {
                        value = "1";
                    } else {
                        value = "SampleValue";
                    }
                }
                if (value == null) value = "SampleValue";
                xml.append("    ").append(value).append("\n");
            }
            // Close the element
            xml.append("  </").append(prefix).append(":").append(localName).append(">\n");
        }
    }
    
    /**
     * Generate XML with specific element value
     */
    public String generateXmlWithValue(String elementName, String value, String namespace) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        
        // Add namespace if needed
        if (namespace != null && !namespace.isEmpty()) {
            String prefix = generator.getDefaultNamespacePrefix();
            
            xml.append("<").append(prefix).append(":").append(elementName);
            
            // Add namespace declarations
            for (Map.Entry<String, String> entry : generator.getNamespaceMap().entrySet()) {
                xml.append(" xmlns:").append(entry.getKey())
                   .append("=\"").append(entry.getValue()).append("\"");
            }
            
            xml.append(">")
               .append(value)
               .append("</").append(prefix).append(":").append(elementName).append(">\n");
        } else {
            xml.append("<").append(elementName).append(">")
               .append(value)
               .append("</").append(elementName).append(">\n");
        }
        
        return xml.toString();
    }
    
    /**
     * Generate XML with specific attribute value
     */
    public String generateXmlWithAttributeValue(String elementName, String attrName, String value, String namespace) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        
        // Add namespace if needed
        if (namespace != null && !namespace.isEmpty()) {
            String prefix = generator.getDefaultNamespacePrefix();
            
            xml.append("<").append(prefix).append(":").append(elementName);
            
            // Add namespace declarations
            for (Map.Entry<String, String> entry : generator.getNamespaceMap().entrySet()) {
                xml.append(" xmlns:").append(entry.getKey())
                   .append("=\"").append(entry.getValue()).append("\"");
            }
            
            xml.append(" ").append(attrName).append("=\"").append(value).append("\">")
               .append("</").append(prefix).append(":").append(elementName).append(">\n");
        } else {
            xml.append("<").append(elementName)
               .append(" ").append(attrName).append("=\"").append(value).append("\">")
               .append("</").append(elementName).append(">\n");
        }
        
        return xml.toString();
    }
    
    /**
     * Generate XML with parent and child element with specific value
     */
    public String generateParentXmlWithChildValue(String parentName, String childName, boolean isReference,
                                                String value, String namespace) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        
        String parentPrefix = generator.getDefaultNamespacePrefix();
        String childPrefix = parentPrefix;
        String localChildName = childName;
        
        if (childName.contains(":")) {
            String[] parts = childName.split(":");
            childPrefix = parts[0];
            localChildName = parts[1];
        }
        
        // Add parent with namespaces
        xml.append("<").append(parentPrefix).append(":").append(parentName);
        
        // Add namespace declarations
        for (Map.Entry<String, String> entry : generator.getNamespaceMap().entrySet()) {
            xml.append(" xmlns:").append(entry.getKey())
               .append("=\"").append(entry.getValue()).append("\"");
        }
        
        xml.append(">\n");
        
        // Add child with value
        xml.append("  <").append(childPrefix).append(":").append(localChildName).append(">")
           .append(value)
           .append("</").append(childPrefix).append(":").append(localChildName).append(">\n");
        
        // Close parent
        xml.append("</").append(parentPrefix).append(":").append(parentName).append(">\n");
        
        return xml.toString();
    }
    
    /**
     * Generate XML with parent, child element, and attribute
     */
    public String generateParentXmlWithChildAttribute(String parentName, String childName, boolean isReference,
                                                    String attrName, String value, String namespace) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        
        String parentPrefix = generator.getDefaultNamespacePrefix();
        String childPrefix = parentPrefix;
        String localChildName = childName;
        
        if (childName.contains(":")) {
            String[] parts = childName.split(":");
            childPrefix = parts[0];
            localChildName = parts[1];
        }
        
        // Add parent with namespaces
        xml.append("<").append(parentPrefix).append(":").append(parentName);
        
        // Add namespace declarations
        for (Map.Entry<String, String> entry : generator.getNamespaceMap().entrySet()) {
            xml.append(" xmlns:").append(entry.getKey())
               .append("=\"").append(entry.getValue()).append("\"");
        }
        
        xml.append(">\n");
        
        // Add child with attribute
        xml.append("  <").append(childPrefix).append(":").append(localChildName)
           .append(" ").append(attrName).append("=\"").append(value).append("\">")
           .append("TestContent")
           .append("</").append(childPrefix).append(":").append(localChildName).append(">\n");
        
        // Close parent
        xml.append("</").append(parentPrefix).append(":").append(parentName).append(">\n");
        
        return xml.toString();
    }
}