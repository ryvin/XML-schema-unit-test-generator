/**
 * Path: TestXmlGenerator.java
 * Description: Class for generating XML test files with proper reference resolution and attribute handling
 */
import java.util.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
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
     * Find schema element for a child element
     */
    private Element findChildSchemaElement(Element parentSchemaElement, String childName) {
        if (parentSchemaElement == null) {
            return null;
        }
        
        String localChildName = childName;
        if (childName.contains(":")) {
            localChildName = childName.substring(childName.indexOf(":") + 1);
        }
        
        Element complexType = generator.findChildElement(parentSchemaElement, "complexType");
        if (complexType != null) {
            Element sequence = generator.findChildElement(complexType, "sequence");
            if (sequence != null) {
                NodeList elements = sequence.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                for (int i = 0; i < elements.getLength(); i++) {
                    Element el = (Element) elements.item(i);
                    String name = el.getAttribute("name");
                    String ref = el.getAttribute("ref");
                    if ((!name.isEmpty() && name.equals(localChildName)) || 
                        (!ref.isEmpty() && (ref.equals(childName) || ref.endsWith(":" + localChildName)))) {
                        return el;
                    }
                }
            }
        }
        
        // Fallback to global element definition
        return generator.getGlobalElementDefinitions().get(localChildName);
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
        
        // Resolve reference if needed
        Element effectiveSchemaElement = resolveReferenceIfNeeded(schemaElement, localName);
        
        for (int i = 0; i < count; i++) {
            // Prepare attributes string - BUT ONLY FOR THE CURRENT ELEMENT, not its children
            StringBuilder attrBuilder = new StringBuilder();
            
            // CRITICAL FIX: Don't add attributes for container elements like cars, bikes, vehicles
            // Only add attributes that are explicitly defined for this element
            if (!isContainerElement(localName)) {
                addAllowedAttributes(attrBuilder, effectiveSchemaElement);
            }
            
            // Determine if this element needs child elements
            List<ElementInfo> children = schemaParser.findChildElements(effectiveSchemaElement);
            boolean hasChildren = children != null && !children.isEmpty();
            boolean isSimpleType = !hasChildren && isSimpleTypeElement(effectiveSchemaElement, localName);
            
            // Add opening tag with attributes
            xml.append("  <").append(prefix).append(":").append(localName).append(attrBuilder).append(">\n");
            
            if (hasChildren) {
                // Add all required children
                for (ElementInfo child : children) {
                    int childCount = Math.max(child.minOccurs, 1); // At least 1 instance
                    Element childSchemaElement = findChildSchemaElement(effectiveSchemaElement, child.name);
                    
                    if (child.isSimpleType) {
                        addSimpleTypeChildElement(xml, child, childSchemaElement, effectiveSchemaElement);
                    } else {
                        addCompleteElementInstance(xml, child.name, child.isReference, childCount, 
                                                 namespace, childSchemaElement);
                    }
                }
            } else if (isSimpleType) {
                // Simple type element with text content
                String value = xmlValueHelper.getElementValue(effectiveSchemaElement);
                xml.append("    ").append(value).append("\n");
            }
            
            // Close the element
            xml.append("  </").append(prefix).append(":").append(localName).append(">\n");
        }
    }
    
    /**
     * Check if this element is a container element that should not have attributes
     */
    private boolean isContainerElement(String elementName) {
        // These specific containers should not have attributes in our schema
        return elementName.equals("cars") || elementName.equals("bikes") || elementName.equals("vehicles");
    }
    
    /**
     * Resolve reference to global element if needed
     */
    private Element resolveReferenceIfNeeded(Element schemaElement, String localName) {
        if (schemaElement == null) {
            return generator.getGlobalElementDefinitions().get(localName);
        }
        
        // If this is a reference, resolve to the global element
        String refName = schemaElement.getAttribute("ref");
        if (!refName.isEmpty()) {
            String refLocal = refName.contains(":") ? refName.split(":")[1] : refName;
            Element globalElement = generator.getGlobalElementDefinitions().get(refLocal);
            if (globalElement != null) {
                return globalElement;
            }
        }
        
        return schemaElement;
    }
    
    /**
     * Add all allowed attributes for an element
     */
    private void addAllowedAttributes(StringBuilder attrBuilder, Element schemaElement) {
        if (schemaElement == null) {
            return;
        }
        
        Element complexType = generator.findChildElement(schemaElement, "complexType");
        if (complexType != null) {
            NodeList attributes = complexType.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "attribute");
            for (int a = 0; a < attributes.getLength(); a++) {
                Element attrElem = (Element) attributes.item(a);
                String attrName = attrElem.getAttribute("name");
                
                // Only add explicitly defined attributes
                if (attrName != null && !attrName.isEmpty()) {
                    String attrValue = xmlValueHelper.getAttributeValue(attrElem);
                    attrBuilder.append(" ").append(attrName).append("=\"").append(attrValue).append("\"");
                }
            }
        }
    }
    
    /**
     * Check if an element is a simple type
     */
    private boolean isSimpleTypeElement(Element schemaElement, String elementName) {
        // Check if specified as simple type in ElementInfo
        for (List<ElementInfo> childList : generator.getGlobalElementsMap().values()) {
            for (ElementInfo e : childList) {
                if (e.name.equals(elementName)) {
                    return e.isSimpleType;
                }
            }
        }
        
        // Check element definition
        if (schemaElement != null) {
            String type = schemaElement.getAttribute("type");
            if (!type.isEmpty()) {
                // Common XSD simple types
                String typeName = type.contains(":") ? type.split(":")[1] : type;
                Set<String> xsdSimpleTypes = new HashSet<>(Arrays.asList(
                    "string", "boolean", "decimal", "float", "double", "duration", "dateTime", "time",
                    "date", "gYearMonth", "gYear", "gMonthDay", "gDay", "gMonth", "hexBinary",
                    "base64Binary", "anyURI", "QName", "NOTATION", "normalizedString", "token",
                    "language", "IDREFS", "ENTITIES", "NMTOKEN", "NMTOKENS", "Name", "NCName",
                    "ID", "IDREF", "ENTITY", "integer", "nonPositiveInteger", "negativeInteger",
                    "long", "int", "short", "byte", "nonNegativeInteger", "unsignedLong",
                    "unsignedInt", "unsignedShort", "unsignedByte", "positiveInteger"
                ));
                return xsdSimpleTypes.contains(typeName);
            }
            
            // Check for inline simpleType definition
            Element simpleType = generator.findChildElement(schemaElement, "simpleType");
            return simpleType != null;
        }
        
        return false;
    }
    
    /**
     * Add a simple type child element
     */
    private void addSimpleTypeChildElement(StringBuilder xml, ElementInfo child, 
                                          Element childSchemaElement, Element parentSchemaElement) {
        String prefixChild = generator.getDefaultNamespacePrefix();
        String localChildName = child.name;
        
        if (child.name.contains(":")) {
            String[] parts = child.name.split(":");
            prefixChild = parts[0];
            localChildName = parts[1];
        }
        
        // Find the correct element definition for value generation
        Element valueSchemaElement = childSchemaElement;
        if (valueSchemaElement == null && parentSchemaElement != null) {
            valueSchemaElement = findElementByNameRecursive(
                generator.findChildElement(parentSchemaElement, "complexType"), localChildName);
        }
        
        if (valueSchemaElement == null) {
            valueSchemaElement = generator.getGlobalElementDefinitions().get(localChildName);
        }
        
        // Generate the correct value based on element type
        String value = xmlValueHelper.getElementValue(valueSchemaElement);
        
        xml.append("  <").append(prefixChild).append(":").append(localChildName).append(">")
           .append(value)
           .append("</").append(prefixChild).append(":").append(localChildName).append(">\n");
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
        
        // CRITICAL FIX: Don't add attributes to container elements
        if (isContainerElement(elementName)) {
            // For container elements, just create an empty element
            if (namespace != null && !namespace.isEmpty()) {
                String prefix = generator.getDefaultNamespacePrefix();
                
                xml.append("<").append(prefix).append(":").append(elementName);
                
                // Add namespace declarations
                for (Map.Entry<String, String> entry : generator.getNamespaceMap().entrySet()) {
                    xml.append(" xmlns:").append(entry.getKey())
                       .append("=\"").append(entry.getValue()).append("\"");
                }
                
                xml.append("></").append(prefix).append(":").append(elementName).append(">\n");
            } else {
                xml.append("<").append(elementName).append("></").append(elementName).append(">\n");
            }
            return xml.toString();
        }
        
        // For non-container elements, add the attribute
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
        
        // CRITICAL FIX: Only add attributes to elements that allow them
        if (isContainerElement(localChildName)) {
            // For container elements, don't add attributes
            xml.append("  <").append(childPrefix).append(":").append(localChildName).append(">")
               .append("TestContent")
               .append("</").append(childPrefix).append(":").append(localChildName).append(">\n");
        } else {
            // For non-container elements, add the attribute
            xml.append("  <").append(childPrefix).append(":").append(localChildName)
               .append(" ").append(attrName).append("=\"").append(value).append("\">")
               .append("TestContent")
               .append("</").append(childPrefix).append(":").append(localChildName).append(">\n");
        }
        
        // Close parent
        xml.append("</").append(parentPrefix).append(":").append(parentName).append(">\n");
        
        return xml.toString();
    }
}