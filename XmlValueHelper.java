/**
 * Path: XmlValueHelper.java
 * Description: Helper class for generating appropriate XML values based on schema types
 */
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import javax.xml.XMLConstants;
import java.util.List;

public class XmlValueHelper {
    private final SchemaParser schemaParser;

    public XmlValueHelper(SchemaParser schemaParser) {
        this.schemaParser = schemaParser;
    }

    /**
     * Generate appropriate value for an attribute based on its definition
     */
    public String getAttributeValue(Element attrElem) {
        if (attrElem == null) return "SampleValue";
        
        // First check if this attribute has enumeration values
        List<String> attrEnums = schemaParser.findEnumerationValues(attrElem);
        if (attrEnums != null && !attrEnums.isEmpty()) {
            // Use the first enumeration value that's not empty
            for (String value : attrEnums) {
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
        }
        
        // If no enumeration values, generate based on type
        String type = attrElem.getAttribute("type");
        return generateValueForType(type);
    }

    /**
     * Generate appropriate value for an element based on its definition
     */
    public String getElementValue(Element schemaElement) {
        if (schemaElement == null) return "SampleValue";
        
        // First check if this element has enumeration values
        List<String> enums = schemaParser.findEnumerationValues(schemaElement);
        System.out.println("[DEBUG] getElementValue for element '" + (schemaElement.getAttribute("name")) + "' direct enums: " + enums);
        if (enums != null && !enums.isEmpty()) {
            for (String value : enums) {
                if (value != null && !value.trim().isEmpty()) {
                    System.out.println("[DEBUG] Returning direct enum value: " + value);
                    return value;
                }
            }
        }

        // --- PATCH: Always resolve type attribute if present, or from parent <complexType> if missing ---
        String type = schemaElement.getAttribute("type");
        if ((type == null || type.isEmpty())) {
            // Try to get type from parent <complexType> if this is inside a <sequence>
            Element parent = (Element) schemaElement.getParentNode();
            if (parent != null && parent.getLocalName() != null && parent.getLocalName().equals("sequence")) {
                Element complexType = (Element) parent.getParentNode();
                if (complexType != null && complexType.getLocalName() != null && complexType.getLocalName().equals("complexType")) {
                    String childName = schemaElement.getAttribute("name");
                    NodeList elements = complexType.getElementsByTagName("element");
                    for (int i = 0; i < elements.getLength(); i++) {
                        Element el = (Element) elements.item(i);
                        // Compare local names only (ignore namespace)
                        String elName = el.getAttribute("name");
                        if (elName != null && !elName.isEmpty() && childName != null && !childName.isEmpty() && elName.equals(childName)) {
                            String childType = el.getAttribute("type");
                            if (childType != null && !childType.isEmpty()) {
                                type = childType;
                                System.out.println("[DEBUG] getElementValue resolved type for element '" + childName + "' from parent complexType: " + type);
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (type != null && !type.isEmpty()) {
            String typeName = type.contains(":") ? type.split(":")[1] : type;
            Element typeDef = schemaParser.resolveTypeDefinition(typeName);
            if (typeDef != null) {
                List<String> typeEnums = schemaParser.findEnumerationValues(typeDef);
                System.out.println("[DEBUG] getElementValue for element '" + (schemaElement.getAttribute("name")) + "' type '" + type + "' enums: " + typeEnums);
                if (typeEnums != null && !typeEnums.isEmpty()) {
                    for (String value : typeEnums) {
                        if (value != null && !value.trim().isEmpty()) {
                            System.out.println("[DEBUG] Returning type enum value: " + value);
                            return value;
                        }
                    }
                }
            } else {
                System.out.println("[DEBUG] No typeDef found for type: " + type);
            }
        }

        // --- Existing logic for inline <simpleType> and parent complexType follows ---
        Element inlineSimpleType = findChildElement(schemaElement, "simpleType");
        if (inlineSimpleType != null) {
            List<String> inlineEnums = schemaParser.findEnumerationValues(inlineSimpleType);
            System.out.println("[DEBUG] getElementValue for element '" + (schemaElement.getAttribute("name")) + "' inline simpleType enums: " + inlineEnums);
            if (inlineEnums != null && !inlineEnums.isEmpty()) {
                for (String value : inlineEnums) {
                    if (value != null && !value.trim().isEmpty()) {
                        System.out.println("[DEBUG] Returning inline simpleType enum value: " + value);
                        return value;
                    }
                }
            }
        } else {
            // Parent <complexType> logic (as previously patched)
            Element parent = (Element) schemaElement.getParentNode();
            if (parent != null && parent.getLocalName() != null && parent.getLocalName().equals("sequence")) {
                Element complexType = (Element) parent.getParentNode();
                if (complexType != null && complexType.getLocalName() != null && complexType.getLocalName().equals("complexType")) {
                    String childName = schemaElement.getAttribute("name");
                    NodeList elements = complexType.getElementsByTagName("element");
                    for (int i = 0; i < elements.getLength(); i++) {
                        Element el = (Element) elements.item(i);
                        if (childName.equals(el.getAttribute("name"))) {
                            Element childSimpleType = findChildElement(el, "simpleType");
                            if (childSimpleType != null) {
                                List<String> childInlineEnums = schemaParser.findEnumerationValues(childSimpleType);
                                System.out.println("[DEBUG] getElementValue for element '" + childName + "' parent complexType inline simpleType enums: " + childInlineEnums);
                                if (childInlineEnums != null && !childInlineEnums.isEmpty()) {
                                    for (String value : childInlineEnums) {
                                        if (value != null && !value.trim().isEmpty()) {
                                            System.out.println("[DEBUG] Returning parent complexType inline enum value: " + value);
                                            return value;
                                        }
                                    }
                                }
                            }
                            Element simpleContent = findChildElement(el, "simpleContent");
                            if (simpleContent != null) {
                                Element restriction = findChildElement(simpleContent, "restriction");
                                if (restriction != null) {
                                    List<String> restrictionEnums = schemaParser.findEnumerationValues(restriction);
                                    System.out.println("[DEBUG] getElementValue for element '" + childName + "' parent complexType simpleContent restriction enums: " + restrictionEnums);
                                    if (restrictionEnums != null && !restrictionEnums.isEmpty()) {
                                        for (String value : restrictionEnums) {
                                            if (value != null && !value.trim().isEmpty()) {
                                                System.out.println("[DEBUG] Returning parent complexType restriction enum value: " + value);
                                                return value;
                                            }
                                        }
                                    }
                                }
                            }
                            String childType = el.getAttribute("type");
                            if (childType != null && !childType.isEmpty()) {
                                String typeName2 = childType.contains(":") ? childType.split(":")[1] : childType;
                                Element typeDef2 = schemaParser.resolveTypeDefinition(typeName2);
                                if (typeDef2 != null) {
                                    List<String> typeEnums2 = schemaParser.findEnumerationValues(typeDef2);
                                    System.out.println("[DEBUG] getElementValue for element '" + childName + "' parent complexType type '" + childType + "' enums: " + typeEnums2);
                                    if (typeEnums2 != null && !typeEnums2.isEmpty()) {
                                        for (String value : typeEnums2) {
                                            if (value != null && !value.trim().isEmpty()) {
                                                System.out.println("[DEBUG] Returning parent complexType enum value: " + value);
                                                return value;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // If no enumeration values, generate based on type
        String fallback = generateValueForType(type);
        System.out.println("[DEBUG] getElementValue fallback for element '" + (schemaElement.getAttribute("name")) + "' type '" + type + "': " + fallback);
        return fallback;
    }
    
    /**
     * Generate value for specific type
     */
    private String generateValueForType(String type) {
        if (type == null || type.isEmpty()) {
            return "SampleValue";
        }
        
        // Remove namespace prefix if present
        String localType = type.contains(":") ? type.substring(type.indexOf(":") + 1) : type;
        
        // Generate value based on type
        if (localType.endsWith("NMTOKEN")) {
            return "ValidNMTOKEN";
        } else if (localType.endsWith("string") || localType.endsWith("normalizedString") || localType.endsWith("token")) {
            return "SampleString";
        } else if (localType.endsWith("int") || localType.endsWith("integer") || localType.endsWith("positiveInteger")) {
            return "1";
        } else if (localType.endsWith("decimal") || localType.endsWith("float") || localType.endsWith("double")) {
            return "1.0";
        } else if (localType.endsWith("boolean")) {
            return "true";
        } else if (localType.endsWith("date")) {
            return "2020-01-01";
        } else if (localType.endsWith("time")) {
            return "12:00:00";
        } else if (localType.endsWith("dateTime")) {
            return "2020-01-01T12:00:00";
        } else if (localType.endsWith("gYear")) {
            return "2022"; // Use a valid year for gYear
        } else if (localType.endsWith("gMonth")) {
            return "01";
        } else if (localType.endsWith("gDay")) {
            return "01";
        } else if (localType.endsWith("gYearMonth")) {
            return "2022-01";
        } else if (localType.endsWith("gMonthDay")) {
            return "--01-01";
        } else if (localType.endsWith("duration")) {
            return "P1D";
        } else {
            return "SampleValue";
        }
    }
    
    /**
     * Check if an attribute is valid for an element by examining its schema definition
     */
    public boolean isAttributeValidForElement(Element elementDef, String attrName) {
        if (elementDef == null) {
            return false;
        }
        
        Element complexType = findChildElement(elementDef, "complexType");
        if (complexType == null) {
            return false;
        }
        
        // Check if this attribute is explicitly defined for this element
        NodeList attributes = complexType.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "attribute");
        for (int i = 0; i < attributes.getLength(); i++) {
            Element attr = (Element) attributes.item(i);
            if (attrName.equals(attr.getAttribute("name"))) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Find a child element with the specified local name
     */
    private Element findChildElement(Element parent, String localName) {
        if (parent == null) return null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                return (Element) child;
            }
        }
        return null;
    }
}