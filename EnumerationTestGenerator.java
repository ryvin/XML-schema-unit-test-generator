import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.XMLConstants;
import java.util.Map;

/**
 * Class for generating enumeration test cases
 */
public class EnumerationTestGenerator {
    
    private ConstraintCrafter generator;
    private TestXmlGenerator xmlGenerator;
    private SchemaParser schemaParser;
    private XmlValueHelper xmlValueHelper;
    
    public EnumerationTestGenerator(ConstraintCrafter generator) {
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
        // Only generate enumeration tests for global elements
        if (!generator.getGlobalElementDefinitions().containsKey(elementName)) {
            ConstraintCrafter.log("[LOG] Skipping enumeration tests for non-global element: " + elementName);
            return;
        }
        
        // Process element's direct type enumerations
        List<String> elementEnums = schemaParser.findEnumerationValues(element);
        ConstraintCrafter.log("[LOG] Element '" + elementName + "' enumeration values: " + elementEnums);
        if (!elementEnums.isEmpty()) {
            generateEnumerationTestsForValues(elementName, elementEnums, targetNamespace, schemaFile);
        }

        // Process attributes with enumerations
        Element complexType = generator.findChildElement(element, "complexType");
        // --- PATCH: If this element has a 'type' attribute referencing a global complexType, descend into it ---
        if (complexType == null) {
            String typeName = element.getAttribute("type");
            ConstraintCrafter.log("[LOG] Element '" + elementName + "' has type attribute: '" + typeName + "'");
            if (!typeName.isEmpty()) {
                String resolvedTypeName = typeName.contains(":") ? typeName.split(":")[1] : typeName;
                ConstraintCrafter.log("[LOG] Attempting to resolve type definition for: '" + resolvedTypeName + "'");
                Element typeDef = schemaParser.resolveTypeDefinition(resolvedTypeName);
                if (typeDef != null) {
                    ConstraintCrafter.log("[LOG] Found type definition for '" + resolvedTypeName + "': " + typeDef.getLocalName());
                } else {
                    ConstraintCrafter.log("[LOG] Could NOT find type definition for '" + resolvedTypeName + "'");
                }
                if (typeDef != null && "complexType".equals(typeDef.getLocalName())) {
                    ConstraintCrafter.log("[LOG] Descending into referenced complexType: " + resolvedTypeName);
                    complexType = typeDef;
                }
            }
        }
        if (complexType != null) {
            NodeList attributes = complexType.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "attribute");
            for (int i = 0; i < attributes.getLength(); i++) {
                Element attribute = (Element) attributes.item(i);
                String attrName = attribute.getAttribute("name");
                List<String> attrEnums = schemaParser.findEnumerationValues(attribute);
                ConstraintCrafter.log("[LOG] Attribute '" + attrName + "' enumeration values: " + attrEnums);
                if (!attrEnums.isEmpty()) {
                    generateAttributeEnumerationTests(elementName, attrName, attrEnums, targetNamespace, schemaFile);
                }
            }
            // --- PATCH: Recursively process child elements for enumerations ---
            ConstraintCrafter.log("[LOG] Recursively checking child elements of complexType for '" + elementName + "'");
            NodeList childElements = complexType.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
            for (int i = 0; i < childElements.getLength(); i++) {
                Element childEl = (Element) childElements.item(i);
                String childName = childEl.getAttribute("name");
                if (childName != null && !childName.isEmpty()) {
                    List<String> childEnums = schemaParser.findEnumerationValues(childEl);
                    ConstraintCrafter.log("[LOG] Child element '" + childName + "' enumeration values: " + childEnums);
                    ConstraintCrafter.log("[LOG] Recursively checking child element for enumeration: " + childName);
                    generateEnumerationTests(childName, childEl, targetNamespace, schemaFile);
                }
            }
        }
        // --- END PATCH ---
        // Existing logic for child elements with enumerations (if any) remains unchanged
        List<ElementInfo> childElements = generator.getGlobalElementsMap().get(elementName);
        if (childElements != null) {
            for (ElementInfo childInfo : childElements) {
                String childName = childInfo.name;
                boolean isReference = childInfo.isReference;
                Element childElement = null;
                // Try to resolve child element definition
                String localChildName = childName.contains(":") ? childName.substring(childName.indexOf(":") + 1) : childName;
                Element parentComplexType = schemaParser.findComplexType(element);
                if (isReference) {
                    childElement = generator.getGlobalElementDefinitions().get(localChildName);
                } else if (parentComplexType != null) {
                    NodeList elements = parentComplexType.getElementsByTagNameNS(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                    for (int i = 0; i < elements.getLength(); i++) {
                        Element el = (Element) elements.item(i);
                        if (localChildName.equals(el.getAttribute("name"))) {
                            childElement = el;
                            break;
                        }
                    }
                }
                // If the child element has enumerations, generate enumeration tests
                List<String> enumValues = schemaParser.findEnumerationValues(childElement);
                if (enumValues != null && !enumValues.isEmpty()) {
                    // --- PATCH: Generate full parent structure, not just the child ---
                    for (String enumValue : enumValues) {
                        String safeValue = enumValue.replaceAll("[^a-zA-Z0-9]", "_");
                        // Build all required children in correct order
                        StringBuilder xml = new StringBuilder();
                        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                        xml.append("<").append(elementName)
                           .append(" xmlns=\"").append(targetNamespace).append("\"")
                           .append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
                           .append(" xsi:schemaLocation=\"").append(targetNamespace).append(" schemas/vehicles.xsd\"")
                           .append(">\n");
                        // Add all children in order, using valid values, but set enum value for the tested child
                        for (ElementInfo info : childElements) {
                            String cname = info.name;
                            String localCname = cname.contains(":") ? cname.substring(cname.indexOf(":") + 1) : cname;
                            Element cElement = null;
                            if (info.isReference) {
                                cElement = generator.getGlobalElementDefinitions().get(localCname);
                            } else if (parentComplexType != null) {
                                NodeList elements = parentComplexType.getElementsByTagNameNS(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                                for (int i = 0; i < elements.getLength(); i++) {
                                    Element el = (Element) elements.item(i);
                                    if (localCname.equals(el.getAttribute("name"))) {
                                        cElement = el;
                                        break;
                                    }
                                }
                            }
                            String value;
                            if (localCname.equals(localChildName)) {
                                value = enumValue;
                            } else {
                                value = xmlValueHelper.getElementValue(cElement);
                            }
                            xml.append("  <").append(localCname).append(">").append(value).append("</").append(localCname).append(">\n");
                        }
                        xml.append("</").append(elementName).append(">\n");
                        String fileName = "test-output/positive/enumeration/" + elementName + "_" + localChildName + "_" + safeValue + ".xml";
                        generator.writeTestFile(fileName, xml.toString());
                        ConstraintCrafter.log("[LOG] Wrote positive child enum test: " + fileName);
                        generator.validateAgainstSchema(fileName, schemaFile, true);
                    }
                    // Negative test with invalid value
                    String invalidValue = "INVALID_" + System.currentTimeMillis();
                    StringBuilder xml = new StringBuilder();
                    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                    xml.append("<").append(elementName)
                       .append(" xmlns=\"").append(targetNamespace).append("\"")
                       .append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
                       .append(" xsi:schemaLocation=\"").append(targetNamespace).append(" schemas/vehicles.xsd\"")
                       .append(">\n");
                    for (ElementInfo info : childElements) {
                        String cname = info.name;
                        String localCname = cname.contains(":") ? cname.substring(cname.indexOf(":") + 1) : cname;
                        Element cElement = null;
                        if (info.isReference) {
                            cElement = generator.getGlobalElementDefinitions().get(localCname);
                        } else if (parentComplexType != null) {
                            NodeList elements = parentComplexType.getElementsByTagNameNS(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                            for (int i = 0; i < elements.getLength(); i++) {
                                Element el = (Element) elements.item(i);
                                if (localCname.equals(el.getAttribute("name"))) {
                                    cElement = el;
                                    break;
                                }
                            }
                        }
                        String value;
                        if (localCname.equals(localChildName)) {
                            value = invalidValue;
                        } else {
                            value = xmlValueHelper.getElementValue(cElement);
                        }
                        xml.append("  <").append(localCname).append(">").append(value).append("</").append(localCname).append(">\n");
                    }
                    xml.append("</").append(elementName).append(">\n");
                    String fileName = "test-output/negative/enumeration/" + elementName + "_" + localChildName + "_invalid.xml";
                    generator.writeTestFile(fileName, xml.toString());
                    ConstraintCrafter.log("[LOG] Wrote negative child enum test: " + fileName);
                    generator.validateAgainstSchema(fileName, schemaFile, false);
                }
            }
        }
    }
    
    /**
     * Generate enumeration tests for an element
     */
    public void generateEnumerationTestsForValues(String elementName, List<String> enumValues, 
                                                String targetNamespace, String schemaFile) throws Exception {
        ConstraintCrafter.log("[LOG] Generating enumeration tests for element: " + elementName + ", enumValues: " + enumValues);
        // Generate positive tests - one for each value
        for (String value : enumValues) {
            String safeValue = value.replaceAll("[^a-zA-Z0-9]", "_");
            // Patch: Use XmlValueHelper to generate valid value for the type if needed
            String xmlValue = value;
            // Optionally, you could resolve the type here, but for enums, value itself is valid
            String fileName = "test-output/positive/enumeration/" + elementName + "_enum_" + safeValue + ".xml";
            String xml = xmlGenerator.generateXmlWithValue(elementName, xmlValue, targetNamespace);
            generator.writeTestFile(fileName, xml);
            ConstraintCrafter.log("[LOG] Wrote positive enum test: " + fileName);
            generator.validateAgainstSchema(fileName, schemaFile, true);
        }
        
        // Generate negative test with invalid value using XmlValueHelper for type
        String fileName = "test-output/negative/enumeration/" + elementName + "_enum_invalid.xml";
        String invalidValue = "INVALID_" + System.currentTimeMillis();
        String xml = xmlGenerator.generateXmlWithValue(elementName, invalidValue, targetNamespace);
        generator.writeTestFile(fileName, xml);
        ConstraintCrafter.log("[LOG] Wrote negative enum test: " + fileName);
        generator.validateAgainstSchema(fileName, schemaFile, false);
    }
    
    /**
     * Generate enumeration tests for child elements
     */
    public void generateChildElementEnumerationTests(String parentName, String childName, boolean isReference,
                                                 List<String> enumValues, String targetNamespace, 
                                                 String schemaFile) throws Exception {
        // Only generate child element enumeration tests if parent is a global element
        if (!generator.getGlobalElementDefinitions().containsKey(parentName)) {
            ConstraintCrafter.log("[LOG] Skipping child enumeration tests for non-global parent: " + parentName);
            return;
        }
        
        ConstraintCrafter.log("[LOG] Generating child element enumeration tests for parent: " + parentName + ", child: " + childName + ", enumValues: " + enumValues);
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
            ConstraintCrafter.log("[LOG] Wrote positive child enum test: " + fileName);
            generator.validateAgainstSchema(fileName, schemaFile, true);
        }
        
        // Generate negative test with invalid value using XmlValueHelper for type
        String fileName = "test-output/negative/enumeration/" + parentName + "_" + localChildName + "_invalid.xml";
        String invalidValue = "INVALID_" + System.currentTimeMillis();
        String xml = xmlGenerator.generateParentXmlWithChildValue(parentName, childName, isReference, invalidValue, targetNamespace);
        generator.writeTestFile(fileName, xml);
        ConstraintCrafter.log("[LOG] Wrote negative child enum test: " + fileName);
        generator.validateAgainstSchema(fileName, schemaFile, false);
    }
    
    /**
     * Generate tests for child element attribute enumerations
     */
    public void generateChildAttributeEnumerationTests(String parentName, String childName, boolean isReference,
                                                   String attrName, List<String> enumValues,
                                                   String targetNamespace, String schemaFile) throws Exception {
        ConstraintCrafter.log("[LOG] Generating child attribute enumeration tests for parent: " + parentName + ", child: " + childName + ", attr: " + attrName + ", enumValues: " + enumValues);
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
            // Try to get from parent's complex type
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
            String xml = generateAppropriateXmlStructure(parentName, childName, isReference, attrName, value, targetNamespace);
            generator.writeTestFile(fileName, xml);
            ConstraintCrafter.log("[LOG] Wrote positive child attribute enum test: " + fileName);
            generator.validateAgainstSchema(fileName, schemaFile, true);
        }
        
        // Generate negative test with invalid value using XmlValueHelper for type
        String fileName = "test-output/negative/enumeration/" + parentName + "_" + localChildName + "_" + attrName + "_invalid.xml";
        String invalidValue = "INVALID_" + System.currentTimeMillis();
        String xml = xmlGenerator.generateParentXmlWithChildAttribute(parentName, childName, isReference, attrName, invalidValue, targetNamespace);
        generator.writeTestFile(fileName, xml);
        ConstraintCrafter.log("[LOG] Wrote negative child attribute enum test: " + fileName);
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
        ConstraintCrafter.log("[LOG] Generating attribute enumeration tests for element: " + elementName + ", attr: " + attrName + ", enumValues: " + enumValues);
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
            ConstraintCrafter.log("[LOG] Wrote positive attribute enum test: " + fileName);
            generator.validateAgainstSchema(fileName, schemaFile, true);
        }
        
        // Generate negative test with invalid value using XmlValueHelper for type
        String fileName = "test-output/negative/enumeration/" + elementName + "_" + attrName + "_invalid.xml";
        String invalidValue = "INVALID_" + System.currentTimeMillis();
        String xml = xmlGenerator.generateXmlWithAttributeValue(elementName, attrName, invalidValue, targetNamespace);
        generator.writeTestFile(fileName, xml);
        ConstraintCrafter.log("[LOG] Wrote negative attribute enum test: " + fileName);
        generator.validateAgainstSchema(fileName, schemaFile, false);
    }
}
