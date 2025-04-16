/**
 * Path: TestXmlGenerator.java
 * Description: Main class for generating XML test files
 */
import java.util.*;
import org.w3c.dom.Element;

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
        
        // Always generate root element with default namespace (no prefix)
        if (namespace != null && !namespace.isEmpty()) {
            xml.append("<").append(parentName);
            xml.append(" xmlns=\"").append(namespace).append("\"");
            xml.append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
            xml.append(" xsi:schemaLocation=\"").append(namespace).append(" schemas/vehicles.xsd\"");
            xml.append(">\n");
        } else {
            xml.append("<").append(parentName).append(">\n");
        }
        
        // Add child elements in the required sequence
        for (ElementInfo childInfo : allChildElements) {
            String childName = childInfo.name;
            // Find the schemaElement for this child
            Element childSchemaElement = schemaParser.findChildElement(parentSchemaElement, childName);
            
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
        
        // Always close root element with no prefix
        if (namespace != null && !namespace.isEmpty()) {
            xml.append("</").append(parentName).append(">\n");
        } else {
            xml.append("</").append(parentName).append(">\n");
        }
        
        return xml.toString();
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

        // --- DEBUG: Print schemaElement info ---
        if (schemaElement == null) {
            XMLSchemaTestGenerator.debug("[DEBUG] addCompleteElementInstance: schemaElement is NULL for element '" + localName + "'");
        } else {
            XMLSchemaTestGenerator.debug("[DEBUG] addCompleteElementInstance: schemaElement for element '" + localName + "' attributes:");
            for (int i = 0; i < schemaElement.getAttributes().getLength(); i++) {
                org.w3c.dom.Node attr = schemaElement.getAttributes().item(i);
                XMLSchemaTestGenerator.debug("    [ATTR] " + attr.getNodeName() + " = '" + attr.getNodeValue() + "'");
            }
            // Print tag name and first 200 chars of outer XML for reference
            String tagName = schemaElement.getTagName();
            String xmlSnippet = schemaElementToString(schemaElement, 200);
            XMLSchemaTestGenerator.debug("    [TAG] " + tagName + " | [XML SNIPPET] " + xmlSnippet);
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
            } else {
                // Always generate a valid value (including for enumerations)
                XMLSchemaTestGenerator.debug("[DEBUG] addCompleteElementInstance for element '" + localName + "' type: '" + (effectiveSchemaElement != null ? effectiveSchemaElement.getAttribute("type") : "null") + "");
                String value = xmlValueHelper.getElementValue(effectiveSchemaElement);
                XMLSchemaTestGenerator.debug("[DEBUG] addCompleteElementInstance value for '" + localName + "': " + value);
                if (value != null && !value.trim().isEmpty()) {
                    xml.append("    ").append(value).append("\n");
                }
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
     * Utility to get a string representation of an Element (for debug)
     */
    private String schemaElementToString(Element el, int maxLen) {
        try {
            javax.xml.transform.Transformer tf = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            tf.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            java.io.StringWriter writer = new java.io.StringWriter();
            tf.transform(new javax.xml.transform.dom.DOMSource(el), new javax.xml.transform.stream.StreamResult(writer));
            String xml = writer.toString();
            if (xml.length() > maxLen) {
                return xml.substring(0, maxLen) + "...";
            }
            return xml;
        } catch (Exception e) {
            return "[ERROR serializing element: " + e.getMessage() + "]";
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
        Element complexType = SchemaParser.findChildElement(elementDef, "complexType");
        if (complexType == null) {
            return attrBuilder;
        }
        // Find all attributes defined for this element
        org.w3c.dom.NodeList attributes = complexType.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "attribute");
        for (int i = 0; i < attributes.getLength(); i++) {
            Element attrElem = (Element) attributes.item(i);
            String attrName = attrElem.getAttribute("name");
            // Skip attributes without a name
            if (attrName == null || attrName.trim().isEmpty()) {
                continue;
            }
            // Always add required attributes, add optional if desired
            String use = attrElem.getAttribute("use");
            boolean isRequired = "required".equalsIgnoreCase(use);
            if (!isRequired && !xmlValueHelper.isAttributeValidForElement(elementDef, attrName)) {
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
        Element simpleType = SchemaParser.findChildElement(element, "simpleType");
        if (simpleType != null) {
            return true;
        }
        
        // Check for complex type with simple content
        Element complexType = SchemaParser.findChildElement(element, "complexType");
        if (complexType != null) {
            Element simpleContent = SchemaParser.findChildElement(complexType, "simpleContent");
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
            Element childSchemaElement = schemaParser.findChildElement(parentElement, child.name);
            // --- DEBUG: Print parent and child info, and whether schema element is found ---
            String parentName = (parentElement != null && parentElement.hasAttribute("name")) ? parentElement.getAttribute("name") : "<null>";
            XMLSchemaTestGenerator.debug("[DEBUG] addChildElements: parent='" + parentName + "', child='" + child.name + "', schemaElement=" + (childSchemaElement == null ? "NULL" : "FOUND"));
            
            // Get child local name
            String childLocalName = child.name;
            if (child.name.contains(":")) {
                childLocalName = child.name.substring(child.name.indexOf(":") + 1);
            }
            
            if (child.isSimpleType) {
                for (int i = 0; i < childCount; i++) {
                    xml.append("    <");
                    if (prefix != null && !prefix.isEmpty()) {
                        xml.append(prefix).append(":");
                    }
                    xml.append(childLocalName).append(">");
                    
                    // Generate appropriate value
                    String value = xmlValueHelper.getElementValue(childSchemaElement);
                    xml.append(value);
                    
                    xml.append("</");
                    if (prefix != null && !prefix.isEmpty()) {
                        xml.append(prefix).append(":");
                    }
                    xml.append(childLocalName).append(">\n");
                }
            } else {
                // Recursively add complex child elements
                addCompleteElementInstance(xml, child.name, child.isReference, childCount, namespace, childSchemaElement);
            }
        }
    }
    
    /**
            localChildName = childName.substring(childName.indexOf(":") + 1);
        }
        Element typeComplexType = null;
        String typeAttr = parentElement.getAttribute("type");
        if (typeAttr != null && !typeAttr.isEmpty()) {
            String typeName = typeAttr.contains(":") ? typeAttr.substring(typeAttr.indexOf(":") + 1) : typeAttr;
            if (schemaParser != null) {
                typeComplexType = schemaParser.resolveTypeDefinition(typeName);
                XMLSchemaTestGenerator.debug("[DEBUG] findChildElement: parent='" + (parentElement.getAttribute("name")) + "', typeAttr='" + typeAttr + "', resolved typeName='" + typeName + "', found complexType=" + (typeComplexType != null));
            }
        }
        if (typeComplexType == null) {
            typeComplexType = SchemaParser.findChildElement(parentElement, "complexType");
            XMLSchemaTestGenerator.debug("[DEBUG] findChildElement: parent='" + (parentElement.getAttribute("name")) + "', inline complexType found=" + (typeComplexType != null));
        }
        if (typeComplexType != null) {
            Element sequence = SchemaParser.findChildElement(typeComplexType, "sequence");
            if (sequence != null) {
                org.w3c.dom.NodeList elements = sequence.getElementsByTagName("element");
                XMLSchemaTestGenerator.debug("[DEBUG] findChildElement: Searching <sequence> for child='" + childName + "'. Found " + elements.getLength() + " elements.");
                for (int i = 0; i < elements.getLength(); i++) {
                    Element el = (Element) elements.item(i);
                    String name = el.getAttribute("name");
                    String ref = el.getAttribute("ref");
                    XMLSchemaTestGenerator.debug("[DEBUG] findChildElement:   sequence child candidate name='" + name + "', ref='" + ref + "'");
                    if ((!name.isEmpty() && name.equals(localChildName)) ||
                        (!ref.isEmpty() && (ref.equals(childName) || ref.endsWith(":" + localChildName)))) {
                        XMLSchemaTestGenerator.debug("[DEBUG] findChildElement:   MATCHED sequence child: '" + name + "' or ref='" + ref + "'");
                        XMLSchemaTestGenerator.debug("[DEBUG] findChildElement: EXIT MATCH parent='" + (parentElement.getAttribute("name")) + "', child='" + childName + "', matched in <sequence>");
                        return el;
                    }
                }
            }
            org.w3c.dom.NodeList directElements = typeComplexType.getElementsByTagName("element");
            XMLSchemaTestGenerator.debug("[DEBUG] findChildElement: Searching <complexType> direct children for child='" + childName + "'. Found " + directElements.getLength() + " elements.");
            for (int i = 0; i < directElements.getLength(); i++) {
                Element el = (Element) directElements.item(i);
                String name = el.getAttribute("name");
                String ref = el.getAttribute("ref");
                XMLSchemaTestGenerator.debug("[DEBUG] findChildElement:   direct child candidate name='" + name + "', ref='" + ref + "'");
                if ((!name.isEmpty() && name.equals(localChildName)) ||
                    (!ref.isEmpty() && (ref.equals(childName) || ref.endsWith(":" + localChildName)))) {
                    XMLSchemaTestGenerator.debug("[DEBUG] findChildElement:   MATCHED direct child: '" + name + "' or ref='" + ref + "'");
                    XMLSchemaTestGenerator.debug("[DEBUG] findChildElement: EXIT MATCH parent='" + (parentElement.getAttribute("name")) + "', child='" + childName + "', matched in <complexType> direct children");
                    return el;
                }
            }
        }
        org.w3c.dom.NodeList children = parentElement.getChildNodes();
        XMLSchemaTestGenerator.debug("[DEBUG] findChildElement: Searching parent direct children for child='" + childName + "'.");
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element el = (Element) child;
                if ("element".equals(el.getLocalName())) {
                    String name = el.getAttribute("name");
                    String ref = el.getAttribute("ref");
                    XMLSchemaTestGenerator.debug("[DEBUG] findChildElement:   parent direct child candidate name='" + name + "', ref='" + ref + "'");
                    if ((!name.isEmpty() && name.equals(localChildName)) ||
                        (!ref.isEmpty() && (ref.equals(childName) || ref.endsWith(":" + localChildName)))) {
                        XMLSchemaTestGenerator.debug("[DEBUG] findChildElement:   MATCHED parent direct child: '" + name + "' or ref='" + ref + "'");
                        XMLSchemaTestGenerator.debug("[DEBUG] findChildElement: EXIT MATCH parent='" + (parentElement.getAttribute("name")) + "', child='" + childName + "', matched in parent direct children");
                        return el;
                    }
                }
            }
        }
        XMLSchemaTestGenerator.debug("[DEBUG] findChildElement: NO MATCH parent='" + (parentElement.getAttribute("name")) + "', child='" + childName + "', returning null or global fallback");
        XMLSchemaTestGenerator.debug("[DEBUG] findChildElement: EXIT NO MATCH parent='" + (parentElement.getAttribute("name")) + "', child='" + childName + "'");
        return generator.getGlobalElementDefinitions().get(localChildName);
    }
    
    /**
     * Generate XML with specific element value
     */
    public String generateXmlWithValue(String elementName, String value, String namespace) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        
        // Add namespace if needed
        if (namespace != null && !namespace.isEmpty()) {
            xml.append("<").append(elementName);
            xml.append(" xmlns=\"").append(namespace).append("\"");
            xml.append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
            xml.append(" xsi:schemaLocation=\"").append(namespace).append(" schemas/vehicles.xsd\"");
            xml.append(">")
               .append(value)
               .append("</").append(elementName).append(">\n");
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
            xml.append("<").append(elementName);
            xml.append(" xmlns=\"").append(namespace).append("\"");
            xml.append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
            xml.append(" xsi:schemaLocation=\"").append(namespace).append(" schemas/vehicles.xsd\"");
            // Only add attribute if it's valid for this element
            if (elementDef == null || xmlValueHelper.isAttributeValidForElement(elementDef, attrName)) {
                xml.append(" ").append(attrName).append("=\"").append(value).append("\"");
            }
            xml.append(">")
               .append("</").append(elementName).append(">\n");
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
        boolean hasParentPrefix = parentPrefix != null && !parentPrefix.isEmpty();
        String childPrefix = parentPrefix;
        boolean hasChildPrefix = childPrefix != null && !childPrefix.isEmpty();
        String localChildName = childName;
        
        if (childName.contains(":")) {
            String[] parts = childName.split(":");
            childPrefix = parts[0];
            localChildName = parts[1];
        }
        
        // Add parent with namespaces
        xml.append("<").append(parentName);
        xml.append(" xmlns=\"").append(namespace).append("\"");
        xml.append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
        xml.append(" xsi:schemaLocation=\"").append(namespace).append(" schemas/vehicles.xsd\"");
        xml.append(">\n");
        
        // Add child with value
        xml.append("  <").append(localChildName).append(">")
           .append(value)
           .append("</").append(localChildName).append(">\n");
        
        // Close parent
        xml.append("</").append(parentName).append(">\n");
        
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
        boolean hasParentPrefix = parentPrefix != null && !parentPrefix.isEmpty();
        String childPrefix = parentPrefix;
        boolean hasChildPrefix = childPrefix != null && !childPrefix.isEmpty();
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
        xml.append("<").append(parentName);
        xml.append(" xmlns=\"").append(namespace).append("\"");
        xml.append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
        xml.append(" xsi:schemaLocation=\"").append(namespace).append(" schemas/vehicles.xsd\"");
        xml.append(">\n");
        
        // Add child with attribute
        xml.append("  <").append(localChildName);
        
        // Only add attribute if it's valid for this element
        if (childDef == null || xmlValueHelper.isAttributeValidForElement(childDef, attrName)) {
            xml.append(" ").append(attrName).append("=\"").append(value).append("\"");
        }
        
        xml.append(">")
           .append("TestContent")
           .append("</").append(localChildName).append(">\n");
        
        // Close parent
        xml.append("</").append(parentName).append(">\n");
        
        return xml.toString();
    }
}