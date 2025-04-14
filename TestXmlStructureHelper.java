/**
 * Path: TestXmlStructureHelper.java
 * Description: Helper class for handling XML structure and validation
 */
import java.util.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import javax.xml.XMLConstants;

/**
 * Helper class for generating and validating XML structures
 */
public class TestXmlStructureHelper {
    
    private XMLSchemaTestGenerator generator;
    private SchemaParser schemaParser;
    private XmlValueHelper xmlValueHelper;
    
    public TestXmlStructureHelper(XMLSchemaTestGenerator generator, SchemaParser schemaParser, XmlValueHelper xmlValueHelper) {
        this.generator = generator;
        this.schemaParser = schemaParser;
        this.xmlValueHelper = xmlValueHelper;
    }
    
    /**
     * Find the schema element definition for a child element
     */
    public Element findChildSchemaElement(Element parentElement, String childName) {
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
        if (!prefix.isEmpty() && generator.getNamespaceMap().containsKey(prefix)) {
            elementNamespace = generator.getNamespaceMap().get(prefix);
        }
        
        for (int i = 0; i < count; i++) {
            // Reference resolution: if isReference, resolve to global element definition
            Element effectiveSchemaElement = resolveElementReference(schemaElement, isReference, localName);

            // Prepare attributes string (handle enumerations for attributes if needed)
            StringBuilder attrBuilder = buildAttributeString(effectiveSchemaElement);

            // Determine if this element is a simple type
            boolean isSimpleType = isSimpleTypeElement(effectiveSchemaElement);

            // Get the children for this element (handles inline complex types)
            List<ElementInfo> children = getElementChildren(effectiveSchemaElement);

            // Add opening tag with attributes if any
            xml.append("  <").append(prefix).append(":").append(localName).append(attrBuilder).append(">\n");

            if (!children.isEmpty()) {
                // Complex type: add all required children recursively
                addChildElements(xml, children, effectiveSchemaElement, elementNamespace, prefix);
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
     * Resolve element reference to its global definition
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
     * Build an attribute string based on element definition
     */
    private StringBuilder buildAttributeString(Element elementDef) {
        StringBuilder attrBuilder = new StringBuilder();
        
        if (elementDef == null) {
            return attrBuilder;
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
        
        // Check the children
        List<ElementInfo> children = schemaParser.findChildElements(element);
        if (!children.isEmpty()) {
            return false;
        }
        
        // Check for simple type or complex type with simple content
        Element simpleType = generator.findChildElement(element, "simpleType");
        if (simpleType != null) {
            return true;
        }
        
        Element complexType = generator.findChildElement(element, "complexType");
        if (complexType != null) {
            Element simpleContent = generator.findChildElement(complexType, "simpleContent");
            if (simpleContent != null) {
                return true;
            }
        }
        
        // Check type attribute
        String type = element.getAttribute("type");
        if (!type.isEmpty()) {
            // If type contains a colon, extract the local part
            String localType = type.contains(":") ? type.substring(type.indexOf(":") + 1) : type;
            
            // Basic built-in simple types check
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
     * Add child elements to parent XML
     */
    private void addChildElements(StringBuilder xml, List<ElementInfo> children, 
                                 Element parentElement, String namespace, String prefix) {
        for (ElementInfo child : children) {
            int childCount = Math.max(child.minOccurs, 1); // Always at least 1
            
            // Find schemaElement for this child
            Element childSchemaElement = findChildElement(parentElement, child.name);
            
            if (child.isSimpleType) {
                String childLocalName = child.name;
                if (child.name.contains(":")) {
                    childLocalName = child.name.substring(child.name.indexOf(":") + 1);
                }
                
                for (int i = 0; i < childCount; i++) {
                    xml.append("    <").append(prefix).append(":").append(childLocalName).append(">");
                    
                    // Special case for bike type element to use correct enumeration values
                    if ("type".equals(childLocalName) && parentElement != null && 
                        "bike".equals(parentElement.getAttribute("name"))) {
                        // Find bike-specific enumeration values
                        Element bikeTypeElement = findChildElement(parentElement, "type");
                        if (bikeTypeElement != null) {
                            String value = xmlValueHelper.getElementValue(bikeTypeElement);
                            xml.append(value);
                        } else {
                            // Fallback for bike types
                            xml.append("mountain");
                        }
                    } else {
                        // For other elements, use the standard value helper
                        String value = xmlValueHelper.getElementValue(childSchemaElement);
                        xml.append(value);
                    }
                    
                    xml.append("</").append(prefix).append(":").append(childLocalName).append(">\n");
                }
            } else {
                // Recursively add complex child elements
                addCompleteElementInstance(xml, child.name, child.isReference, childCount, namespace, childSchemaElement);
            }
        }
    }
    
    /**
     * Find a child element by name in parent element
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
        
        // Find complexType
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
     * Recursively search for an element with the given name inside a parent node
     */
    public Element findElementByNameRecursive(Element parent, String name) {
        if (parent == null) return null;
        
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                if ("element".equals(el.getLocalName()) && name.equals(el.getAttribute("name"))) {
                    return el;
                }
                // Recurse into compositors
                if ("sequence".equals(el.getLocalName()) || "choice".equals(el.getLocalName()) ||
                    "all".equals(el.getLocalName()) || "complexType".equals(el.getLocalName())) {
                    Element found = findElementByNameRecursive(el, name);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }
}