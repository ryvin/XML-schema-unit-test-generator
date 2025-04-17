/**
 * Path: XmlValueHelper.java
 * Description: Helper class for generating appropriate XML values based on schema types
 */
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
        if (attrElem == null) {
            return generateValueForType(null);
        }
        
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
        if (schemaElement == null) {
            ConstraintCrafter.debug("getElementValue: schemaElement is null, generating fallback value");
            return generateValueForType(null);
        }
        
        // First check if this element has enumeration values
        List<String> enums = schemaParser.findEnumerationValues(schemaElement);
        ConstraintCrafter.debug("getElementValue for element '" + (schemaElement.getAttribute("name")) + "' direct enums: " + enums);
        if (enums != null && !enums.isEmpty()) {
            for (String value : enums) {
                if (value != null && !value.trim().isEmpty()) {
                    ConstraintCrafter.debug("getElementValue: Returning first non-empty enum value '" + value + "' for element '" + schemaElement.getAttribute("name") + "'");
                    return value;
                }
            }
            // No direct enums, continue to type/pattern-based value generation
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
                                ConstraintCrafter.debug("getElementValue resolved type for element '" + childName + "' from parent complexType: " + type);
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
                ConstraintCrafter.debug("getElementValue for element '" + (schemaElement.getAttribute("name")) + "' type '" + type + "' enums: " + typeEnums);
                if (typeEnums != null && !typeEnums.isEmpty()) {
                    for (String value : typeEnums) {
                        if (value != null && !value.trim().isEmpty()) {
                            ConstraintCrafter.debug("getElementValue: Returning first non-empty type enum value '" + value + "' for element '" + schemaElement.getAttribute("name") + "'");
                            return value;
                        }
                    }
                }
            } else {
                ConstraintCrafter.debug("No typeDef found for type: " + type);
            }
        }

        // --- Existing logic for inline <simpleType> and parent complexType follows ---
        Element inlineSimpleType = SchemaParser.findChildElement(schemaElement, "simpleType");
        if (inlineSimpleType != null) {
            List<String> inlineEnums = schemaParser.findEnumerationValues(inlineSimpleType);
            ConstraintCrafter.debug("getElementValue for element '" + (schemaElement.getAttribute("name")) + "' inline simpleType enums: " + inlineEnums);
            if (inlineEnums != null && !inlineEnums.isEmpty()) {
                for (String value : inlineEnums) {
                    if (value != null && !value.trim().isEmpty()) {
                        ConstraintCrafter.debug("getElementValue: Returning first non-empty inline simpleType enum value '" + value + "' for element '" + schemaElement.getAttribute("name") + "'");
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
                            Element childSimpleType = SchemaParser.findChildElement(el, "simpleType");
                            if (childSimpleType != null) {
                                List<String> childInlineEnums = schemaParser.findEnumerationValues(childSimpleType);
                                ConstraintCrafter.debug("getElementValue for element '" + childName + "' parent complexType inline simpleType enums: " + childInlineEnums);
                                if (childInlineEnums != null && !childInlineEnums.isEmpty()) {
                                    for (String value : childInlineEnums) {
                                        if (value != null && !value.trim().isEmpty()) {
                                            ConstraintCrafter.debug("getElementValue: Returning first non-empty parent complexType inline enum value '" + value + "' for element '" + childName + "'");
                                            return value;
                                        }
                                    }
                                }
                            }
                            Element simpleContent = SchemaParser.findChildElement(el, "simpleContent");
                            if (simpleContent != null) {
                                Element restriction = SchemaParser.findChildElement(simpleContent, "restriction");
                                if (restriction != null) {
                                    List<String> restrictionEnums = schemaParser.findEnumerationValues(restriction);
                                    ConstraintCrafter.debug("getElementValue for element '" + childName + "' parent complexType simpleContent restriction enums: " + restrictionEnums);
                                    if (restrictionEnums != null && !restrictionEnums.isEmpty()) {
                                        for (String value : restrictionEnums) {
                                            if (value != null && !value.trim().isEmpty()) {
                                                ConstraintCrafter.debug("getElementValue: Returning first non-empty parent complexType restriction enum value '" + value + "' for element '" + childName + "'");
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
                                    ConstraintCrafter.debug("getElementValue for element '" + childName + "' parent complexType type '" + childType + "' enums: " + typeEnums2);
                                    if (typeEnums2 != null && !typeEnums2.isEmpty()) {
                                        for (String value : typeEnums2) {
                                            if (value != null && !value.trim().isEmpty()) {
                                                ConstraintCrafter.debug("getElementValue: Returning first non-empty parent complexType enum value '" + value + "' for element '" + childName + "'");
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
        ConstraintCrafter.debug("getElementValue fallback for element '" + (schemaElement.getAttribute("name")) + "' type '" + type + "': " + fallback);
        return fallback;
    }
    
    /**
     * Generate value for specific type
     */
    public String generateValueForType(String type) {
        if (type == null || type.isEmpty()) {
            return "1"; // fallback: minimal valid value
        }
        // Remove namespace prefix if present
        String localType = type.contains(":") ? type.substring(type.indexOf(":") + 1) : type;

        // Check for restrictions (pattern, min/max, length, etc.)
        // This requires SchemaParser to expose restriction info, assumed as pseudo-code:
        // Restrictions restrictions = schemaParser.getRestrictionsForType(type);
        // For now, simulate with utility methods (to be implemented in SchemaParser):
        String pattern = schemaParser.findPatternForType(type);
        Integer minLength = schemaParser.findMinLengthForType(type);
        Integer maxLength = schemaParser.findMaxLengthForType(type);
        String minInclusive = schemaParser.findMinInclusiveForType(type);
        String maxInclusive = schemaParser.findMaxInclusiveForType(type);

        // Pattern restriction (for strings)
        if (pattern != null && !pattern.isEmpty()) {
            return generateStringMatchingPattern(pattern, minLength, maxLength);
        }
        // Length restriction (for strings)
        if (minLength != null || maxLength != null) {
            int len = (minLength != null) ? minLength : 1;
            if (maxLength != null && len > maxLength) len = maxLength;
            return generateFixedLengthString(len);
        }
        // Numeric range restrictions
        if (minInclusive != null || maxInclusive != null) {
            try {
                // Try to parse as decimal first
                java.math.BigDecimal min = (minInclusive != null) ? new java.math.BigDecimal(minInclusive) : java.math.BigDecimal.ZERO;
                java.math.BigDecimal max = (maxInclusive != null) ? new java.math.BigDecimal(maxInclusive) : min.add(java.math.BigDecimal.TEN);
                java.math.BigDecimal value = min.add(max).divide(new java.math.BigDecimal("2"));
                // Return as integer if type is integer, else as string/decimal
                if (localType.endsWith("int") || localType.endsWith("integer") || localType.endsWith("positiveInteger") || localType.endsWith("long") || localType.endsWith("short") || localType.endsWith("byte")) {
                    return Integer.toString(value.intValue());
                } else {
                    return value.stripTrailingZeros().toPlainString();
                }
            } catch (Exception e) {
                // Fallback to safe default
                if (localType.endsWith("decimal") || localType.endsWith("float") || localType.endsWith("double")) {
                    return "1.0";
                } else {
                    return "1";
                }
            }
        }
        // Generate value based on type
        if (localType.endsWith("NMTOKEN")) {
            return "NMTOKEN123";
        } else if (localType.endsWith("string") || localType.endsWith("normalizedString") || localType.endsWith("token")) {
            return "ABC";
        } else if (localType.endsWith("int") || localType.endsWith("integer") || localType.endsWith("positiveInteger")) {
            return "1";
        } else if (localType.endsWith("decimal") || localType.endsWith("float") || localType.endsWith("double")) {
            return "1.0";
        } else if (localType.endsWith("boolean")) {
            return "true";
        } else if (localType.endsWith("date")) {
            return "2023-01-01";
        } else if (localType.endsWith("time")) {
            return "12:00:00";
        } else if (localType.endsWith("dateTime")) {
            return "2023-01-01T12:00:00";
        } else if (localType.endsWith("gYear")) {
            return "2022";
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
            return "1";
        }
    }

    // Utility: generate a string of fixed length (A-Z)
    private String generateFixedLengthString(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append((char)('A' + (i % 26)));
        }
        return sb.toString();
    }

    // Utility: generate a string matching a simple pattern (placeholder, real impl should use regexgen)
    private String generateStringMatchingPattern(String pattern, Integer minLength, Integer maxLength) {
        if (pattern == null || pattern.isEmpty()) {
            int len = (minLength != null) ? minLength : 3;
            return generateFixedLengthString(len);
        }
        StringBuilder sb = new StringBuilder();
        java.util.regex.Matcher literalMatcher = java.util.regex.Pattern.compile("^(.*?)\\[").matcher(pattern);
        if (literalMatcher.find()) {
            sb.append(literalMatcher.group(1));
        }
        if (pattern.contains("[A-Z]")) {
            sb.append("A");
        }
        java.util.regex.Matcher countMatcher = java.util.regex.Pattern.compile("\\{(\\d+)\\}").matcher(pattern);
        while (countMatcher.find()) {
            int count = Integer.parseInt(countMatcher.group(1));
            for (int i = 0; i < count; i++) {
                sb.append('1');
            }
        }
        if (sb.length() == 0) {
            int lenFallback = (minLength != null) ? minLength : 3;
            return generateFixedLengthString(lenFallback);
        }
        return sb.toString();
    }
    
    /**
     * Check if an attribute is valid for an element by examining its schema definition
     */
    public boolean isAttributeValidForElement(Element elementDef, String attrName) {
        if (elementDef == null) {
            return false;
        }
        
        Element complexType = SchemaParser.findChildElement(elementDef, "complexType");
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
}
