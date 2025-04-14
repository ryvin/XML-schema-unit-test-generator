import java.util.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

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
     * Generate XML for testing cardinality constraints
     */
    public String generateTestXml(String parentName, List<ElementInfo> allChildElements,
                                String targetChildName, int occurrences, boolean isReference, 
                                String namespace, Element parentSchemaElement) {
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
            Element childSchemaElement = findSchemaElementForChild(parentSchemaElement, childName);
            
            // Skip elements we're not testing in this case and maintain the right sequence
            if (!childName.equals(targetChildName)) {
                // Add a single instance of non-target elements to satisfy minimum requirements
                if (childInfo.minOccurs > 0) {
                    addCompleteElementInstance(xml, childName, childInfo.isReference, 
                                              1, namespace, childSchemaElement);
                }
                continue;
            }
            
            // For the target element, add the specified number of occurrences
            addCompleteElementInstance(xml, childName, isReference, occurrences, 
                                      namespace, childSchemaElement);
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
     * Find schema element for a child element by searching in compositors
     */
    private Element findSchemaElementForChild(Element parentSchemaElement, String childName) {
        if (parentSchemaElement == null) {
            return null;
        }
        
        Element childSchemaElement = null;
        Element complexType = generator.findChildElement(parentSchemaElement, "complexType");
        
        if (complexType != null) {
            // Check all compositors: sequence, choice, all
            for (String compositor : new String[]{"sequence", "choice", "all"}) {
                Element compositorElement = generator.findChildElement(complexType, compositor);
                if (compositorElement != null) {
                    NodeList elements = compositorElement.getElementsByTagNameNS(
                            javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                    
                    for (int i = 0; i < elements.getLength(); i++) {
                        Element el = (Element) elements.item(i);
                        String name = el.getAttribute("name");
                        String ref = el.getAttribute("ref");
                        
                        if ((!name.isEmpty() && name.equals(childName)) || 
                            (!ref.isEmpty() && ref.equals(childName))) {
                            childSchemaElement = el;
                            break;
                        }
                    }
                }
                if (childSchemaElement != null) break;
            }
        }
        
        // Fallback to global element definition
        if (childSchemaElement == null && generator.getGlobalElementDefinitions().containsKey(childName)) {
            childSchemaElement = generator.getGlobalElementDefinitions().get(childName);
        }
        
        return childSchemaElement;
    }
    
    /**
     * Add complete element instances with appropriate structure based on element name
     */
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
            Element effectiveSchemaElement = resolveReferenceElement(schemaElement, isReference, localName);

            // Prepare attributes string (handle enumerations for attributes if needed)
            StringBuilder attrBuilder = new StringBuilder();
            addAttributesToBuilder(attrBuilder, effectiveSchemaElement);

            // Determine if this element is a simple type
            boolean isSimpleType = false;
            // Use SchemaParser to get children for this element (handles inline complex types)
            List<ElementInfo> children = new ArrayList<>();
            if (effectiveSchemaElement != null) {
                children = schemaParser.findChildElements(effectiveSchemaElement);
            }

            // Try to find ElementInfo for this element
            ElementInfo info = findElementInfo(localName, children);
            isSimpleType = determineIfSimpleType(info, localName, children);

            // Add opening tag with attributes if any
            xml.append("  <").append(prefix).append(":").append(localName).append(attrBuilder).append(">\n");

            if (children != null && !children.isEmpty()) {
                // Complex type: always add all required children recursively, no text content
                generateChildElements(xml, children, effectiveSchemaElement, elementNamespace);
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
     * Resolve element references to their global definitions
     */
    private Element resolveReferenceElement(Element schemaElement, boolean isReference, String localName) {
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
        
        return effectiveSchemaElement;
    }
    
    /**
     * Add attributes to XML element
     */
    private void addAttributesToBuilder(StringBuilder attrBuilder, Element effectiveSchemaElement) {
        if (effectiveSchemaElement != null) {
            // Handle attributes
            Element complexType = generator.findChildElement(effectiveSchemaElement, "complexType");
            if (complexType != null) {
                NodeList attributes = complexType.getElementsByTagNameNS(
                        javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI, "attribute");
                
                for (int a = 0; a < attributes.getLength(); a++) {
                    Element attrElem = (Element) attributes.item(a);
                    String attrName = attrElem.getAttribute("name");
                    
                    // Only add attribute if it is explicitly defined for this element
                    if (attrName == null || attrName.trim().isEmpty()) continue;
                    
                    // Never add 'type' as an attribute unless it is explicitly defined as an attribute
                    if (attrName.equals("type") && 
                        (!attrElem.getTagName().endsWith("attribute") || !attrElem.hasAttribute("name"))) {
                        continue;
                    }
                    
                    String attrValue = xmlValueHelper.getAttributeValue(attrElem);
                    attrBuilder.append(" ").append(attrName).append("=\"").append(attrValue).append("\"");
                }
            }
        }
    }
    
    /**
     * Find ElementInfo for a given element name
     */
    private ElementInfo findElementInfo(String localName, List<ElementInfo> children) {
        if (!children.isEmpty()) {
            return null;
        }
        
        for (List<ElementInfo> childList : generator.getGlobalElementsMap().values()) {
            for (ElementInfo e : childList) {
                if (e.name.equals(localName)) {
                    return e;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Determine if an element is a simple type
     */
    private boolean determineIfSimpleType(ElementInfo info, String localName, List<ElementInfo> children) {
        if (!children.isEmpty()) {
            return false;
        }
        
        if (info != null) {
            return info.isSimpleType;
        }
        
        // If we can't determine, default to false
        return false;
    }
    
    /**
     * Generate child elements for complex elements
     */
    private void generateChildElements(StringBuilder xml, List<ElementInfo> children, 
                                      Element effectiveSchemaElement, String elementNamespace) {
        for (ElementInfo child : children) {
            int childCount = Math.max(child.minOccurs, 1); // Always at least 1
            
            // Find schemaElement for child
            Element childSchemaElement = findChildSchemaElement(child, effectiveSchemaElement);
            
            // If child is a simple type, generate value directly
            if (child.isSimpleType) {
                generateSimpleTypeChildElement(xml, child, effectiveSchemaElement, childSchemaElement);
            } else {
                // Recursive call for complex types
                addCompleteElementInstance(xml, child.name, child.isReference, childCount, 
                                         elementNamespace, childSchemaElement);
            }
        }
    }
    
    /**
     * Find schema element for a child element
     */
    private Element findChildSchemaElement(ElementInfo child, Element parentElement) {
        Element childSchemaElement = null;
        
        if (parentElement != null) {
            Element complexType = generator.findChildElement(parentElement, "complexType");
            if (complexType != null) {
                for (String compositor : new String[]{"sequence", "choice", "all"}) {
                    Element compositorElement = generator.findChildElement(complexType, compositor);
                    if (compositorElement != null) {
                        NodeList elements = compositorElement.getElementsByTagNameNS(
                                javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                        
                        for (int j = 0; j < elements.getLength(); j++) {
                            Element el = (Element) elements.item(j);
                            String name = el.getAttribute("name");
                            String ref = el.getAttribute("ref");
                            
                            if ((!name.isEmpty() && name.equals(child.name)) || 
                                (!ref.isEmpty() && ref.equals(child.name))) {
                                childSchemaElement = el;
                                break;
                            }
                        }
                    }
                    if (childSchemaElement != null) break;
                }
            }
        }
        
        // Fallback to global element definition if not found inline
        if (childSchemaElement == null && generator.getGlobalElementDefinitions().containsKey(child.name)) {
            childSchemaElement = generator.getGlobalElementDefinitions().get(child.name);
        }
        
        return childSchemaElement;
    }
    
    /**
     * Generate simple type child element with value
     */
    private void generateSimpleTypeChildElement(StringBuilder xml, ElementInfo child, 
                                               Element parentElement, Element childSchemaElement) {
        String prefixChild = generator.getDefaultNamespacePrefix();
        String localChildName = child.name;
        
        if (child.name.contains(":")) {
            String[] parts = child.name.split(":");
            prefixChild = parts[0];
            localChildName = parts[1];
        }
        
        xml.append("  <").append(prefixChild).append(":").append(localChildName).append(">");
        
        // Find the correct <xs:element> node for this child within the parent complexType
        Element valueSchemaElement = findElementByNameRecursive(parentElement, localChildName);
        
        // Fallback to previous logic if not found
        if (valueSchemaElement == null) {
            if (childSchemaElement != null && "element".equals(childSchemaElement.getLocalName())) {
                valueSchemaElement = childSchemaElement;
            } else if (generator.getGlobalElementDefinitions().containsKey(child.name)) {
                valueSchemaElement = generator.getGlobalElementDefinitions().get(child.name);
            }
        }
        
        // If this is a reference, resolve to the referenced global element
        if (valueSchemaElement != null && valueSchemaElement.hasAttribute("ref")) {
            String refName = valueSchemaElement.getAttribute("ref");
            String refLocal = refName.contains(":") ? refName.split(":")[1] : refName;
            
            if (generator.getGlobalElementDefinitions().containsKey(refLocal)) {
                valueSchemaElement = generator.getGlobalElementDefinitions().get(refLocal);
            }
        }
        
        // Generate appropriate value
        String value = xmlValueHelper.getElementValue(valueSchemaElement);
        xml.append(value);
        xml.append("</").append(prefixChild).append(":").append(localChildName).append(">\n");
    }
    
    /**
     * Recursively search for <xs:element> with the given name inside a parent node
     */
    private Element findElementByNameRecursive(Element parent, String name) {
        if (parent == null) return null;
        
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                
                if ("element".equals(el.getLocalName()) && name.equals(el.getAttribute("name"))) {
                    return el;
                }
                
                // Recurse into <xs:sequence>, <xs:choice>, <xs:all>, <xs:complexType>
                if ("sequence".equals(el.getLocalName()) || "choice".equals(el.getLocalName()) ||
                    "all".equals(el.getLocalName()) || "complexType".equals(el.getLocalName())) {
                    Element found = findElementByNameRecursive(el, name);
                    if (found != null) return found;
                }
            }
        }
        
        return null;
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