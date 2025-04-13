import java.util.*;
import org.w3c.dom.Element;

/**
 * Class for generating XML test files
 */
public class TestXmlGenerator {
    
    private XMLSchemaTestGenerator generator;
    
    public TestXmlGenerator(XMLSchemaTestGenerator generator) {
        this.generator = generator;
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
            // Add the element with proper namespace prefix
            xml.append("  <").append(prefix).append(":").append(localName).append(">\n");
            
            // If this is a reference to a global element or a global element itself
            if (isReference || isGlobalElement) {
                List<ElementInfo> children = generator.getGlobalElementsMap().get(localName);
                if (children != null && !children.isEmpty()) {
                    // Add child elements recursively
                    for (ElementInfo child : children) {
                        if (child.minOccurs > 0) {
                            addCompleteElementInstance(xml, child.name, child.isReference, child.minOccurs, elementNamespace);
                        }
                    }
                } else {
                    // For simple type elements or elements with no children defined
                    xml.append("    TestValue").append(i + 1).append("\n");
                }
            } else {
                // For elements that aren't global or references - generate test content
                xml.append("    TestValue").append(i + 1).append("\n");
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