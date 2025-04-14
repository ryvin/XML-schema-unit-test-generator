import java.util.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Class for generating XML test files
 */
public class TestXmlGenerator {
    
    private XMLSchemaTestGenerator generator;
    private SchemaParser schemaParser;
    private XmlValueHelper xmlValueHelper;
    
    public TestXmlGenerator(XMLSchemaTestGenerator generator, SchemaParser schemaParser) {
        this.generator = generator;
        this.schemaParser = schemaParser;
        this.xmlValueHelper = new XmlValueHelper(schemaParser);
    }
    
    /**
     /**
      * Generate XML for testing cardinality constraints
      */
     public String generateTestXml(String parentName, List<ElementInfo> allChildElements,
                                   String targetChildName, int occurrences, boolean isReference, String namespace, Element parentSchemaElement) {
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
             // Find the schemaElement for this child
             Element childSchemaElement = null;
             if (parentSchemaElement != null) {
                 Element complexType = generator.findChildElement(parentSchemaElement, "complexType");
                 if (complexType != null) {
                     Element sequence = generator.findChildElement(complexType, "sequence");
                     if (sequence != null) {
                         org.w3c.dom.NodeList elements = sequence.getElementsByTagNameNS(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                         for (int i = 0; i < elements.getLength(); i++) {
                             Element el = (Element) elements.item(i);
                             String name = el.getAttribute("name");
                             String ref = el.getAttribute("ref");
                             if ((!name.isEmpty() && name.equals(childName)) || (!ref.isEmpty() && ref.equals(childName))) {
                                 childSchemaElement = el;
                                 break;
                             }
                         }
                     }
                 }
             }
             // Fallback to global element definition if not found inline
             if (childSchemaElement == null && generator.getGlobalElementDefinitions().containsKey(childName)) {
                 childSchemaElement = generator.getGlobalElementDefinitions().get(childName);
             }
             
             // Skip elements we're not testing in this case and maintain the right sequence
             if (!childName.equals(targetChildName)) {
                 // Add a single instance of non-target elements to satisfy minimum requirements
                 if (childInfo.minOccurs > 0) {
                     addCompleteElementInstance(xml, childName, childInfo.isReference, 1, namespace, childSchemaElement);
                 }
                 continue;
             }
             
             // For the target element, add the specified number of occurrences
             addCompleteElementInstance(xml, childName, isReference, occurrences, namespace, childSchemaElement);
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
    // Updated to accept schemaElement for correct reference resolution
    public void addCompleteElementInstance(StringBuilder xml, String elementName, boolean isReference,
                                           int count, String namespace, Element schemaElement) {
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
        
        for (int i = 0; i < count; i++) {
            // Reference resolution: if isReference, resolve to global element definition
            Element effectiveSchemaElement = schemaElement;
            if (isReference && schemaElement != null) {
                // If schemaElement is a reference, resolve to the global element it points to
                String refName = schemaElement.getAttribute("ref");
                if (!refName.isEmpty()) {
                    String refLocal = refName.contains(":") ? refName.split(":")[1] : refName;
                    if (generator.getGlobalElementDefinitions().containsKey(refLocal)) {
                        effectiveSchemaElement = generator.getGlobalElementDefinitions().get(refLocal);
                    }
                }
            }
            // If not found, fallback to global element by localName
            if (effectiveSchemaElement == null && generator.getGlobalElementDefinitions().containsKey(localName)) {
                effectiveSchemaElement = generator.getGlobalElementDefinitions().get(localName);
            }

            // Prepare attributes string (handle enumerations for attributes if needed)
            StringBuilder attrBuilder = new StringBuilder();
            if (effectiveSchemaElement != null) {
                // Handle attributes
                Element complexType = generator.findChildElement(effectiveSchemaElement, "complexType");
                if (complexType != null) {
                    NodeList attributes = complexType.getElementsByTagNameNS(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI, "attribute");
                    for (int a = 0; a < attributes.getLength(); a++) {
                        Element attrElem = (Element) attributes.item(a);
                        String attrName = attrElem.getAttribute("name");
                        // Only add 'type' if it is explicitly defined as an attribute, not as a type definition
                        if (attrName == null || attrName.trim().isEmpty()) continue;
                        if (attrName.equals("type") && !attrElem.getTagName().endsWith("attribute")) continue;
                        String attrValue = xmlValueHelper.getAttributeValue(attrElem);
                        attrBuilder.append(" ").append(attrName).append("=\"").append(attrValue).append("\"");
                    }
                }
            }

            // Determine if this element is a simple type
            boolean isSimpleType = false;
            // Use SchemaParser to get children for this element (handles inline complex types)
            List<ElementInfo> children = new ArrayList<>();
            if (effectiveSchemaElement != null) {
                children = schemaParser.findChildElements(effectiveSchemaElement);
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
                    // Find schemaElement for child
                    Element childSchemaElement = null;
                    if (effectiveSchemaElement != null) {
                        Element complexType = generator.findChildElement(effectiveSchemaElement, "complexType");
                        if (complexType != null) {
                            Element sequence = generator.findChildElement(complexType, "sequence");
                            if (sequence != null) {
                                NodeList elements = sequence.getElementsByTagNameNS(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                                for (int j = 0; j < elements.getLength(); j++) {
                                    Element el = (Element) elements.item(j);
                                    String name = el.getAttribute("name");
                                    String ref = el.getAttribute("ref");
                                    if ((!name.isEmpty() && name.equals(child.name)) || (!ref.isEmpty() && ref.equals(child.name))) {
                                        childSchemaElement = el;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    // Fallback to global element definition if not found inline
                    if (childSchemaElement == null && generator.getGlobalElementDefinitions().containsKey(child.name)) {
                        childSchemaElement = generator.getGlobalElementDefinitions().get(child.name);
                    }
                    // If child is a simple type, generate value directly
                    if (child.isSimpleType) {
                        String prefixChild = generator.getDefaultNamespacePrefix();
                        String localChildName = child.name;
                        if (child.name.contains(":")) {
                            String[] parts = child.name.split(":");
                            prefixChild = parts[0];
                            localChildName = parts[1];
                        }
                        xml.append("  <").append(prefixChild).append(":").append(localChildName).append(">");
                        // Always use the correct schema node for value generation
                        Element valueSchemaElement = childSchemaElement;
                        // If this is a reference, resolve to the global element definition
                        if (valueSchemaElement != null && valueSchemaElement.hasAttribute("ref")) {
                            String refName = valueSchemaElement.getAttribute("ref");
                            String refLocal = refName.contains(":") ? refName.split(":")[1] : refName;
                            if (generator.getGlobalElementDefinitions().containsKey(refLocal)) {
                                valueSchemaElement = generator.getGlobalElementDefinitions().get(refLocal);
                            }
                        }
                        // If still null, fallback to the current child name in global definitions
                        if (valueSchemaElement == null && generator.getGlobalElementDefinitions().containsKey(child.name)) {
                            valueSchemaElement = generator.getGlobalElementDefinitions().get(child.name);
                        }
                        String value = xmlValueHelper.getElementValue(valueSchemaElement);
                        xml.append(value);
                        xml.append("</").append(prefixChild).append(":").append(localChildName).append(">\n");
                    } else {
                        addCompleteElementInstance(xml, child.name, child.isReference, childCount, elementNamespace, childSchemaElement);
                    }
                }
            } else if (isSimpleType) {
                // Only add text content for simple types with no children
                String value = xmlValueHelper.getElementValue(effectiveSchemaElement);
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