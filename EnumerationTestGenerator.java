import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.XMLConstants;
import java.util.Map;

/**
 * Class for generating enumeration test cases
 */
public class EnumerationTestGenerator {
    
    private XMLSchemaTestGenerator generator;
    private TestXmlGenerator xmlGenerator;
    private SchemaParser schemaParser;
    private XmlValueHelper xmlValueHelper;
    
    public EnumerationTestGenerator(XMLSchemaTestGenerator generator) {
        this.generator = generator;
        this.schemaParser = new SchemaParser(generator);
        this.xmlValueHelper = new XmlValueHelper(schemaParser);
        this.xmlGenerator = new TestXmlGenerator(generator, this.schemaParser);
    }
    
    /**
     * Generate tests for enumeration constraints
     */
    public void generateEnumerationTests(String elementName, Element element, 
                                       String targetNamespace, String schemaFile) throws Exception {
        // Process element's direct type enumerations
        List<String> elementEnums = schemaParser.findEnumerationValues(element);
        if (!elementEnums.isEmpty()) {
            generateEnumerationTestsForValues(elementName, elementEnums, targetNamespace, schemaFile);
        }
        
        // Process attributes with enumerations
        Element complexType = generator.findChildElement(element, "complexType");
        if (complexType != null) {
            NodeList attributes = complexType.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "attribute");
            for (int i = 0; i < attributes.getLength(); i++) {
                Element attribute = (Element) attributes.item(i);
                String attrName = attribute.getAttribute("name");
                List<String> attrEnums = schemaParser.findEnumerationValues(attribute);
                
                if (!attrEnums.isEmpty()) {
                    generateAttributeEnumerationTests(elementName, attrName, attrEnums, targetNamespace, schemaFile);
                }
            }
        }
        
        // Process child elements with enumerations
        List<ElementInfo> childElements = generator.getGlobalElementsMap().get(elementName);
        if (childElements != null) {
            for (ElementInfo childInfo : childElements) {
                String childName = childInfo.name;
                
                // Extract local name if it's a qualified name with prefix
                String localChildName = childName;
                if (childName.contains(":")) {
                    localChildName = childName.substring(childName.indexOf(":") + 1);
                }
                
                // Find the element definition
                Element childElement = null;
                if (childInfo.isReference) {
                    childElement = generator.getGlobalElementDefinitions().get(localChildName);
                } else if (complexType != null) {
                    // Find the element in the complex type
                    NodeList elements = complexType.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                    for (int i = 0; i < elements.getLength(); i++) {
                        Element el = (Element) elements.item(i);
                        if (localChildName.equals(el.getAttribute("name"))) {
                            childElement = el;
                            break;
                        }
                    }
                }
                
                if (childElement != null) {
                    // Find enumeration values for this child element
                    List<String> childEnums = schemaParser.findEnumerationValues(childElement);
                    if (!childEnums.isEmpty()) {
                        // Generate tests for this element's enum values
                        generateChildElementEnumerationTests(elementName, childName, childInfo.isReference, 
                                                          childEnums, targetNamespace, schemaFile);
                    }
                    
                    // Find enumerations in child elements' attributes
                    Element childComplexType = generator.findChildElement(childElement, "complexType");
                    if (childComplexType != null) {
                        NodeList childAttributes = childComplexType.getElementsByTagNameNS(
                                XMLConstants.W3C_XML_SCHEMA_NS_URI, "attribute");
                        
                        for (int i = 0; i < childAttributes.getLength(); i++) {
                            Element attribute = (Element) childAttributes.item(i);
                            String attrName = attribute.getAttribute("name");
                            List<String> attrEnums = schemaParser.findEnumerationValues(attribute);
                            
                            if (!attrEnums.isEmpty()) {
                                // Generate tests for child element attribute enumerations
                                generateChildAttributeEnumerationTests(
                                    elementName, childName, childInfo.isReference, 
                                    attrName, attrEnums, targetNamespace, schemaFile);
                            }
                        }
                    }
                    
                    // Recursively process this element's enumerations if it's a global element
                    if (generator.getGlobalElementDefinitions().containsKey(localChildName)) {
                        generateEnumerationTests(localChildName, generator.getGlobalElementDefinitions().get(localChildName), 
                                              targetNamespace, schemaFile);
                    }
                }
            }
        }
    }
    
    /**
     * Generate enumeration tests for an element
     */
    public void generateEnumerationTestsForValues(String elementName, List<String> enumValues, 
                                                String targetNamespace, String schemaFile) throws Exception {
        // Generate positive tests - one for each value
        for (String value : enumValues) {
            String safeValue = value.replaceAll("[^a-zA-Z0-9]", "_");
            String fileName = "test-output/positive/enumeration/" + elementName + "_enum_" + safeValue + ".xml";
            String xml = xmlGenerator.generateXmlWithValue(elementName, value, targetNamespace);
            generator.writeTestFile(fileName, xml);
            generator.validateAgainstSchema(fileName, schemaFile, true);
        }
        
        // Generate negative test with invalid value
        String fileName = "test-output/negative/enumeration/" + elementName + "_enum_invalid.xml";
        String invalidValue = "INVALID_" + System.currentTimeMillis();
        String xml = xmlGenerator.generateXmlWithValue(elementName, invalidValue, targetNamespace);
        generator.writeTestFile(fileName, xml);
        generator.validateAgainstSchema(fileName, schemaFile, false);
    }
    
    /**
     * Generate enumeration tests for child elements
     */
    public void generateChildElementEnumerationTests(String parentName, String childName, boolean isReference,
                                                 List<String> enumValues, String targetNamespace, 
                                                 String schemaFile) throws Exception {
        // Extract local name if it's a qualified name with prefix
        String localChildName = childName;
        if (childName.contains(":")) {
            localChildName = childName.substring(childName.indexOf(":") + 1);
        }
        
        // Generate positive tests - one for each value
        for (String value : enumValues) {
            String safeValue = value.replaceAll("[^a-zA-Z0-9]", "_");
            String fileName = "test-output/positive/enumeration/" + parentName + "_" + localChildName + "_" + safeValue + ".xml";
            String xml = xmlGenerator.generateParentXmlWithChildValue(parentName, childName, isReference, value, targetNamespace);
            generator.writeTestFile(fileName, xml);
            generator.validateAgainstSchema(fileName, schemaFile, true);
        }
        
        // Generate negative test with invalid value
        String fileName = "test-output/negative/enumeration/" + parentName + "_" + localChildName + "_invalid.xml";
        String invalidValue = "INVALID_" + System.currentTimeMillis();
        String xml = xmlGenerator.generateParentXmlWithChildValue(parentName, childName, isReference, invalidValue, targetNamespace);
        generator.writeTestFile(fileName, xml);
        generator.validateAgainstSchema(fileName, schemaFile, false);
    }
    
    /**
     * Generate tests for child element attribute enumerations
     */
    public void generateChildAttributeEnumerationTests(String parentName, String childName, boolean isReference,
                                                   String attrName, List<String> enumValues,
                                                   String targetNamespace, String schemaFile) throws Exception {
        // Extract local name if it's a qualified name with prefix
        String localChildName = childName;
        if (childName.contains(":")) {
            localChildName = childName.substring(childName.indexOf(":") + 1);
        }

        // Check if the attribute is actually allowed on this element
        Element childElement = null;
        if (isReference) {
            childElement = generator.getGlobalElementDefinitions().get(localChildName);
        } else {
            Element parentElement = generator.getGlobalElementDefinitions().get(parentName);
            if (parentElement != null) {
                Element complexType = generator.findChildElement(parentElement, "complexType");
                if (complexType != null) {
                    NodeList elements = complexType.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                    for (int i = 0; i < elements.getLength(); i++) {
                        Element el = (Element) elements.item(i);
                        if (localChildName.equals(el.getAttribute("name"))) {
                            childElement = el;
                            break;
                        }
                    }
                }
            }
        }
        
        // Skip if attribute is not valid for this element
        if (childElement != null && !xmlValueHelper.isAttributeValidForElement(childElement, attrName)) {
            return;
        }
        
        // Generate positive tests - one for each value
        for (String value : enumValues) {
            String safeValue = value.replaceAll("[^a-zA-Z0-9]", "_");
            String fileName = "test-output/positive/enumeration/" + parentName + "_" + localChildName + "_" + attrName + "_" + safeValue + ".xml";
            
            // Generate XML with the appropriate structure based on the element requirements
            String xml = generateAppropriateXmlStructure(parentName, childName, isReference, attrName, value, targetNamespace);
            generator.writeTestFile(fileName, xml);
            generator.validateAgainstSchema(fileName, schemaFile, true);
        }
        
        // Generate negative test with invalid value
        String fileName = "test-output/negative/enumeration/" + parentName + "_" + localChildName + "_" + attrName + "_invalid.xml";
        String invalidValue = "INVALID_" + System.currentTimeMillis();
        String xml = xmlGenerator.generateParentXmlWithChildAttribute(parentName, childName, isReference, attrName, invalidValue, targetNamespace);
        generator.writeTestFile(fileName, xml);
        generator.validateAgainstSchema(fileName, schemaFile, false);
    }
    
    /**
     * Generate appropriate XML structure based on element and attribute requirements
     */
    private String generateAppropriateXmlStructure(String parentName, String childName, boolean isReference,
                                                String attrName, String attrValue, String targetNamespace) {
        // Extract local name if it's a qualified name with prefix
        String localChildName = childName;
        if (childName.contains(":")) {
            localChildName = childName.substring(childName.indexOf(":") + 1);
        }
        
        // Find the element definition to determine required structure
        Element element = null;
        if (isReference) {
            element = generator.getGlobalElementDefinitions().get(localChildName);
        } else {
            // Try to get from parent's complex type
            Element parentElement = generator.getGlobalElementDefinitions().get(parentName);
            if (parentElement != null) {
                Element complexType = generator.findChildElement(parentElement, "complexType");
                if (complexType != null) {
                    NodeList elements = complexType.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                    for (int i = 0; i < elements.getLength(); i++) {
                        Element el = (Element) elements.item(i);
                        if (localChildName.equals(el.getAttribute("name"))) {
                            element = el;
                            break;
                        }
                    }
                }
            }
        }
        
        // If we have the element definition, check for required children
        List<ElementInfo> requiredChildren = null;
        if (element != null) {
            requiredChildren = schemaParser.findChildElements(element);
        }
        
        // Generate XML with required structure
        if (requiredChildren != null && !requiredChildren.isEmpty()) {
            // Construct XML with required children
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            
            // Add opening parent element with namespaces
            String prefix = generator.getDefaultNamespacePrefix();
            xml.append("<").append(prefix).append(":").append(parentName);
            for (Map.Entry<String, String> entry : generator.getNamespaceMap().entrySet()) {
                xml.append(" xmlns:").append(entry.getKey())
                   .append("=\"").append(entry.getValue()).append("\"");
            }
            xml.append(">\n");
            
            // Add child element with attribute
            xml.append("  <").append(prefix).append(":").append(localChildName);
            
            // Only add the attribute if it's valid for this element
            if (element == null || xmlValueHelper.isAttributeValidForElement(element, attrName)) {
                xml.append(" ").append(attrName).append("=\"").append(attrValue).append("\"");
            }
            
            xml.append(">\n");
            
            // Add all required child elements
            for (ElementInfo childInfo : requiredChildren) {
                String childLocalName = childInfo.name;
                if (childInfo.name.contains(":")) {
                    childLocalName = childInfo.name.substring(childInfo.name.indexOf(":") + 1);
                }
                
                // Find the child element definition
                Element childElement = null;
                if (childInfo.isReference) {
                    childElement = generator.getGlobalElementDefinitions().get(childLocalName);
                }
                
                if (childInfo.isSimpleType) {
                    xml.append("    <").append(prefix).append(":").append(childLocalName).append(">");
                    
                    // Generate appropriate value based on the child element's definition
                    String value = xmlValueHelper.getElementValue(childElement);
                    xml.append(value);
                    
                    xml.append("</").append(prefix).append(":").append(childLocalName).append(">\n");
                }
            }
            
            // Close child element
            xml.append("  </").append(prefix).append(":").append(localChildName).append(">\n");
            
            // Close parent element
            xml.append("</").append(prefix).append(":").append(parentName).append(">\n");
            
            return xml.toString();
        } else {
            // Use default implementation if no special structure is required
            return xmlGenerator.generateParentXmlWithChildAttribute(parentName, childName, isReference, attrName, attrValue, targetNamespace);
        }
    }
    
    /**
     * Generate enumeration tests for an attribute
     */
    public void generateAttributeEnumerationTests(String elementName, String attrName, List<String> enumValues,
                                               String targetNamespace, String schemaFile) throws Exception {
        // Get the element definition to check if attribute is valid
        String localName = elementName;
        if (elementName.contains(":")) {
            localName = elementName.substring(elementName.indexOf(":") + 1);
        }
        
        Element elementDef = generator.getGlobalElementDefinitions().get(localName);
        
        // Skip if attribute is not valid for this element
        if (elementDef != null && !xmlValueHelper.isAttributeValidForElement(elementDef, attrName)) {
            return;
        }
        
        // Generate positive tests - one for each value
        for (String value : enumValues) {
            String safeValue = value.replaceAll("[^a-zA-Z0-9]", "_");
            String fileName = "test-output/positive/enumeration/" + elementName + "_" + attrName + "_" + safeValue + ".xml";
            String xml = xmlGenerator.generateXmlWithAttributeValue(elementName, attrName, value, targetNamespace);
            generator.writeTestFile(fileName, xml);
            generator.validateAgainstSchema(fileName, schemaFile, true);
        }
        
        // Generate negative test with invalid value
        String fileName = "test-output/negative/enumeration/" + elementName + "_" + attrName + "_invalid.xml";
        String invalidValue = "INVALID_" + System.currentTimeMillis();
        String xml = xmlGenerator.generateXmlWithAttributeValue(elementName, attrName, invalidValue, targetNamespace);
        generator.writeTestFile(fileName, xml);
        generator.validateAgainstSchema(fileName, schemaFile, false);
    }
}