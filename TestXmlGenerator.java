/**
 * Path: TestXmlGenerator.java
 * Description: Main class for generating XML test files
 */
import java.util.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.XMLConstants;

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
            Element childSchemaElement = findChildSchemaElement(parentSchemaElement, childName);
            
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
     * Find the schema element definition for a child element
     */
    private Element findChildSchemaElement(Element parentElement, String childName) {
        if (parentElement == null) {
            return null;
        }
        
        // Extract local name if it's a qualified name with prefix
        String localChildName = childName;
        if (childName.contains(":")) {
            localChildName = childName.substring(childName.indexOf(":") + 1);
        }
        
        // First try to find it as a direct child
        Element complexType = generator.findChildElement(parentElement, "complexType");
        if (complexType != null) {
            // Look in sequence, choice, or all
            for (String compositorName : new String[]{"sequence", "choice", "all"}) {
                Element compositor = generator.findChildElement(complexType, compositorName);
                if (compositor != null) {
                    NodeList elements = compositor.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                    for (int i = 0; i < elements.getLength(); i++) {
                        Element el = (Element) elements.item(i);
                        if (el.getParentNode() != compositor) {
                            continue;
                        }
                        
                        String name = el.getAttribute("name");
                        String ref = el.getAttribute("ref");
                        
                        if ((!name.isEmpty() && name.equals(localChildName)) || 
                            (!ref.isEmpty() && (ref.equals(childName) || ref.endsWith(":" + localChildName)))) {
                            return el;
                        }
                    }
                }
            }
        }
        
        // If not found as direct child, try global element definitions
        return generator.getGlobalElementDefinitions().get(localChildName);
    }
    
    /**
     * Add complete element instances with appropriate structure
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
        if (prefix != null && !prefix.isEmpty() && generator.getNamespaceMap().containsKey(prefix)) {
            elementNamespace = generator.getNamespaceMap().get(prefix);
        }
        
        for (int i = 0; i < count; i++) {
            // Reference resolution: if isReference, resolve to global element definition
            Element effectiveSchemaElement = resolveElementReference(schemaElement, isReference, localName);

            // Build attributes string based on element definition
            StringBuilder attrBuilder = buildAttributeString(effectiveSchemaElement);

            // Determine if this element is a simple type and get its children
            boolean isSimpleType = isSimpleTypeElement(effectiveSchemaElement);
            List<ElementInfo> children = getElementChildren(effectiveSchemaElement);

            // Add opening tag with attributes
            xml.append("  <");
            if (prefix != null && !prefix.isEmpty()) {
                xml.append(prefix).append(":");
            }
            xml.append(localName).append(attrBuilder).append(">\n");

            if (!children.isEmpty()) {
                // Complex type: add all required children
                addChildElements(xml, children, effectiveSchemaElement, elementNamespace, prefix);
            } else if (isSimpleType) {
                // Simple type: add text content
                String value = xmlValueHelper.getElementValue(effectiveSchemaElement);
                xml.append("    ").append(value).append("\n");
            }
            
            // Close the element
            xml.append("  </");
            if (prefix != null && !prefix.isEmpty()) {
                xml.append(prefix).append(":");
            }
            xml.append(localName).append(">\n");
        }
    }
    
    /**
     * Resolve element reference to global definition
     */
    private Element resolveElementReference(Element schemaElement, boolean isReference, String localName) {
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
     * Build attribute string for an element based on its schema definition
     */
    private StringBuilder buildAttributeString(Element elementDef) {
        StringBuilder attrBuilder = new StringBuilder();
        if (elementDef == null) {
            return attrBuilder;
        }
        // Prevent adding attributes to container elements like 'cars', 'bikes', 'vehicles'
        String localName = elementDef.getAttribute("name");
        if (localName != null) {
            String lower = localName.toLowerCase();
            if (lower.equals("cars") || lower.equals("bikes") || lower.equals("vehicles")) {
                return attrBuilder;
            }
        }
        // Find complex type definition
        Element complexType = generator.findChildElement(elementDef, "complexType");
        if (complexType == null) {
            return attrBuilder;
        }
        // Find all attributes defined for this element
        NodeList attributes = complexType.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "attribute");
        for (int i = 0; i < attributes.getLength(); i++) {
            Element attrElem = (Element) attributes.item(i);
            String attrName = attrElem.getAttribute("name");
            // Skip attributes without a name
            if (attrName == null || attrName.trim().isEmpty()) {
                continue;
            }
            // Verify this attribute belongs to this element
            if (!xmlValueHelper.isAttributeValidForElement(elementDef, attrName)) {
                continue;
            }
            // Get the attribute value
            String attrValue = xmlValueHelper.getAttributeValue(attrElem);
            attrBuilder.append(" ").append(attrName).append("=\"").append(attrValue).append("\"");
        }
        return attrBuilder;
    }
    
    /**
     * Check if an element is a simple type
     */
    private boolean isSimpleTypeElement(Element element) {
        if (element == null) {
            return false;
        }
        
        // Check if element has children
        List<ElementInfo> children = schemaParser.findChildElements(element);
        if (!children.isEmpty()) {
            return false;
        }
        
        // Check for simple type definition
        Element simpleType = generator.findChildElement(element, "simpleType");
        if (simpleType != null) {
            return true;
        }
        
        // Check for complex type with simple content
        Element complexType = generator.findChildElement(element, "complexType");
        if (complexType != null) {
            Element simpleContent = generator.findChildElement(complexType, "simpleContent");
            if (simpleContent != null) {
                return true;
            }
        }
        
        // Check type attribute for built-in simple types
        String type = element.getAttribute("type");
        if (!type.isEmpty()) {
            // Remove namespace prefix if present
            String localType = type.contains(":") ? type.substring(type.indexOf(":") + 1) : type;
            
            // Common built-in simple types
            if (localType.endsWith("string") || localType.endsWith("integer") || 
                localType.endsWith("decimal") || localType.endsWith("boolean") || 
                localType.endsWith("date") || localType.endsWith("time") ||
                localType.endsWith("gYear")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get children for an element
     */
    private List<ElementInfo> getElementChildren(Element element) {
        if (element == null) {
            return Collections.emptyList();
        }
        
        return schemaParser.findChildElements(element);
    }
    
    /**
     * Add child elements to XML
     */
    private void addChildElements(StringBuilder xml, List<ElementInfo> children, Element parentElement, 
                                 String namespace, String prefix) {
        for (ElementInfo child : children) {
            int childCount = Math.max(child.minOccurs, 1); // Always at least 1
            
            // Find schema element for this child
            Element childSchemaElement = findChildElement(parentElement, child.name);
            
            // Get child local name
            String childLocalName = child.name;
            if (child.name.contains(":")) {
                childLocalName = child.name.substring(child.name.indexOf(":") + 1);
            }
            
            if (child.isSimpleType) {
                for (int i = 0; i < childCount; i++) {
                    xml.append("    <").append(prefix).append(":").append(childLocalName).append(">");
                    
                    // Generate appropriate value
                    String value = xmlValueHelper.getElementValue(childSchemaElement);
                    xml.append(value);
                    
                    xml.append("</").append(prefix).append(":").append(childLocalName).append(">\n");
                }
            } else {
                // Recursively add complex child elements
                addCompleteElementInstance(xml, child.name, child.isReference, childCount, namespace, childSchemaElement);
            }
        }
    }
    
    /**
     * Find a child element by name
     */
    private Element findChildElement(Element parentElement, String childName) {
        if (parentElement == null) {
            return null;
        }
        
        // Extract local name if it's a qualified name with prefix
        String localChildName = childName;
        if (childName.contains(":")) {
            localChildName = childName.substring(childName.indexOf(":") + 1);
        }
        
        // Find complex type
        Element complexType = generator.findChildElement(parentElement, "complexType");
        if (complexType == null) {
            return null;
        }
        
        // Look for child element in sequence, choice, or all
        for (String compositorName : new String[]{"sequence", "choice", "all"}) {
            Element compositor = generator.findChildElement(complexType, compositorName);
            if (compositor != null) {
                NodeList elements = compositor.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                for (int i = 0; i < elements.getLength(); i++) {
                    Element el = (Element) elements.item(i);
                    
                    // Skip if not direct child of compositor
                    if (el.getParentNode() != compositor) {
                        continue;
                    }
                    
                    String name = el.getAttribute("name");
                    String ref = el.getAttribute("ref");
                    
                    if ((!name.isEmpty() && name.equals(localChildName)) || 
                        (!ref.isEmpty() && (ref.equals(childName) || ref.endsWith(":" + localChildName)))) {
                        return el;
                    }
                }
            }
        }
        
        // Element not found in parent
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
        
        // Get the element definition
        String localName = elementName;
        if (elementName.contains(":")) {
            localName = elementName.substring(elementName.indexOf(":") + 1);
        }
        Element elementDef = generator.getGlobalElementDefinitions().get(localName);
        
        // Add namespace if needed
        if (namespace != null && !namespace.isEmpty()) {
            String prefix = generator.getDefaultNamespacePrefix();
            
            xml.append("<").append(prefix).append(":").append(elementName);
            
            // Add namespace declarations
            for (Map.Entry<String, String> entry : generator.getNamespaceMap().entrySet()) {
                xml.append(" xmlns:").append(entry.getKey())
                   .append("=\"").append(entry.getValue()).append("\"");
            }
            
            // Only add attribute if it's valid for this element
            if (elementDef == null || xmlValueHelper.isAttributeValidForElement(elementDef, attrName)) {
                xml.append(" ").append(attrName).append("=\"").append(value).append("\"");
            }
            
            xml.append(">")
               .append("</").append(prefix).append(":").append(elementName).append(">\n");
        } else {
            xml.append("<").append(elementName);
            
            // Only add attribute if it's valid for this element
            if (elementDef == null || xmlValueHelper.isAttributeValidForElement(elementDef, attrName)) {
                xml.append(" ").append(attrName).append("=\"").append(value).append("\"");
            }
            
            xml.append(">")
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
        
        // Find child element definition
        String localName = localChildName;
        Element childDef = generator.getGlobalElementDefinitions().get(localName);
        
        // Add parent with namespaces
        xml.append("<").append(parentPrefix).append(":").append(parentName);
        
        // Add namespace declarations
        for (Map.Entry<String, String> entry : generator.getNamespaceMap().entrySet()) {
            xml.append(" xmlns:").append(entry.getKey())
               .append("=\"").append(entry.getValue()).append("\"");
        }
        
        xml.append(">\n");
        
        // Add child with attribute
        xml.append("  <").append(childPrefix).append(":").append(localChildName);
        
        // Only add attribute if it's valid for this element
        if (childDef == null || xmlValueHelper.isAttributeValidForElement(childDef, attrName)) {
            xml.append(" ").append(attrName).append("=\"").append(value).append("\"");
        }
        
        xml.append(">")
           .append("TestContent")
           .append("</").append(childPrefix).append(":").append(localChildName).append(">\n");
        
        // Close parent
        xml.append("</").append(parentPrefix).append(":").append(parentName).append(">\n");
        
        return xml.toString();
    }
}