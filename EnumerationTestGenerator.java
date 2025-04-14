import java.util.List;
import java.util.ArrayList;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.XMLConstants;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Class for generating enumeration test cases
 */
public class EnumerationTestGenerator {
    private static final Logger logger = Logger.getLogger(EnumerationTestGenerator.class.getName());
    private XMLSchemaTestGenerator generator;
    private TestXmlGenerator xmlGenerator;
    private SchemaParser schemaParser;
    
    public EnumerationTestGenerator(XMLSchemaTestGenerator generator) {
        this.generator = generator;
        this.schemaParser = new SchemaParser(generator);
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
                Element childElement = findChildElement(childInfo, localChildName);
                
                if (childElement != null) {
                    // Find enumeration values for this child element
                    List<String> childEnums = schemaParser.findEnumerationValues(childElement);
                    if (!childEnums.isEmpty()) {
                        // Generate tests for this element's enum values
                        generateChildElementEnumerationTests(elementName, childName, childInfo.isReference, 
                                                          childEnums, targetNamespace, schemaFile);
                    }
                    
                    // Find enumerations in child elements' attributes
                    processChildElementAttributes(elementName, childName, childInfo, childElement, 
                                                 targetNamespace, schemaFile);
                    
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
     * Find child element based on ElementInfo
     */
    private Element findChildElement(ElementInfo childInfo, String localChildName) {
        Element childElement = null;
        if (childInfo.isReference) {
            childElement = generator.getGlobalElementDefinitions().get(localChildName);
        } else {
            // Try to find element in global definitions
            childElement = generator.getGlobalElementDefinitions().get(localChildName);
        }
        return childElement;
    }
    
    /**
     * Process attributes of a child element for enumeration tests
     */
    private void processChildElementAttributes(String parentName, String childName, ElementInfo childInfo, 
                                             Element childElement, String targetNamespace, 
                                             String schemaFile) throws Exception {
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
                        parentName, childName, childInfo.isReference, 
                        attrName, attrEnums, targetNamespace, schemaFile);
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

        // Get the schema for this element to determine its structure
        Element childElement = null;
        if (generator.getGlobalElementDefinitions().containsKey(localChildName)) {
            childElement = generator.getGlobalElementDefinitions().get(localChildName);
        }

        // Generate positive tests - one for each value
        for (String value : enumValues) {
            String safeValue = value.replaceAll("[^a-zA-Z0-9]", "_");
            String fileName = "test-output/positive/enumeration/" + parentName + "_" + localChildName + "_" + attrName + "_" + safeValue + ".xml";
            
            // Generate the XML based on element's schema structure
            String xml = generateChildAttributeXml(parentName, childName, childElement, 
                                                attrName, value, targetNamespace);
            
            generator.writeTestFile(fileName, xml);
            generator.validateAgainstSchema(fileName, schemaFile, true);
        }
        
        // Generate negative test with invalid value
        String fileName = "test-output/negative/enumeration/" + parentName + "_" + localChildName + "_" + attrName + "_invalid.xml";
        String invalidValue = "INVALID_" + System.currentTimeMillis();
        
        String xml = generateChildAttributeXml(parentName, childName, childElement, 
                                            attrName, invalidValue, targetNamespace);
        
        generator.writeTestFile(fileName, xml);
        generator.validateAgainstSchema(fileName, schemaFile, false);
    }
    
    /**
     * Generate XML for child element with attribute
     * This method generates appropriate XML based on element structure
     */
    private String generateChildAttributeXml(String parentName, String childName, Element childElement,
                                          String attrName, String attrValue, String targetNamespace) {
        // If we have schema information for the child, use it to generate valid XML
        if (childElement != null) {
            // Get child elements required by the schema
            List<ElementInfo> childElements = schemaParser.findChildElements(childElement);
            
            // If child has required children, we need to generate a more complete structure
            if (!childElements.isEmpty()) {
                return generateStructuredXmlWithAttribute(parentName, childName, childElement, 
                                                        childElements, attrName, attrValue, targetNamespace);
            }
        }
        
        // Fallback to simple case if no child elements or no schema info
        return xmlGenerator.generateParentXmlWithChildAttribute(
            parentName, childName, false, attrName, attrValue, targetNamespace);
    }
    
    /**
     * Generate structured XML with attributes and required child elements
     */
    private String generateStructuredXmlWithAttribute(String parentName, String childName, Element childElement,
                                                   List<ElementInfo> childElements, String attrName, 
                                                   String attrValue, String targetNamespace) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        
        String prefix = generator.getDefaultNamespacePrefix();
        
        // Add opening parent with namespaces
        xml.append("<").append(prefix).append(":").append(parentName);
        for (Map.Entry<String, String> entry : generator.getNamespaceMap().entrySet()) {
            xml.append(" xmlns:").append(entry.getKey())
               .append("=\"").append(entry.getValue()).append("\"");
        }
        xml.append(">\n");
        
        // Add child with attribute
        xml.append("  <").append(prefix).append(":").append(childName)
           .append(" ").append(attrName).append("=\"").append(attrValue).append("\">\n");
        
        // Add all required child elements
        for (ElementInfo grandchildInfo : childElements) {
            if (grandchildInfo.minOccurs > 0) {
                String grandchildName = grandchildInfo.name;
                String localGrandchildName = grandchildName;
                
                if (grandchildName.contains(":")) {
                    String[] parts = grandchildName.split(":");
                    localGrandchildName = parts[1];
                }
                
                xml.append("    <").append(prefix).append(":").append(localGrandchildName).append(">");
                
                // Find schema element for the grandchild to determine its value
                Element grandchildElement = null;
                if (generator.getGlobalElementDefinitions().containsKey(localGrandchildName)) {
                    grandchildElement = generator.getGlobalElementDefinitions().get(localGrandchildName);
                }
                
                // Generate appropriate value
                String value;
                if (grandchildElement != null) {
                    value = new XmlValueHelper(schemaParser).getElementValue(grandchildElement);
                } else {
                    value = "SampleValue";
                }
                
                xml.append(value);
                xml.append("</").append(prefix).append(":").append(localGrandchildName).append(">\n");
            }
        }
        
        // Close child element
        xml.append("  </").append(prefix).append(":").append(childName).append(">\n");
        
        // Close parent element
        xml.append("</").append(prefix).append(":").append(parentName).append(">\n");
        
        return xml.toString();
    }
    
    /**
     * Generate enumeration tests for an attribute
     */
    public void generateAttributeEnumerationTests(String elementName, String attrName, List<String> enumValues,
                                               String targetNamespace, String schemaFile) throws Exception {
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