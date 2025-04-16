import org.w3c.dom.Element;
import java.util.List;
import org.w3c.dom.NodeList;
import javax.xml.XMLConstants;

/**
 * Utility for generating XML values (element/attribute) based on schema constraints.
 * Produces schema-accurate values for enumerations, patterns, min/max, and types.
 */
public class XmlValueHelper {
    private final SchemaParser schemaParser;

    public XmlValueHelper(SchemaParser schemaParser) {
        this.schemaParser = schemaParser;
    }

    // Generate attribute value based on type or enumeration
    /**
     * Generate a value for an attribute based on schema constraints (enumeration, restriction, type).
     * @param attrElem The XML schema attribute element.
     * @return A valid value for the attribute.
     */
    public String getAttributeValue(Element attrElem) {
        // Only use enumerations if this is an xs:attribute node (not element)
        if (!"attribute".equals(attrElem.getLocalName())) {
            return "";
        }
        List<String> attrEnums = schemaParser.findEnumerationValues(attrElem);
        if (!attrEnums.isEmpty()) {
            for (String v : attrEnums) {
                if (v != null && !v.trim().isEmpty()) {
                    return v;
                }
            }
        }
        // Check for restrictions
        String restrictionValue = getValueFromRestrictions(attrElem);
        if (restrictionValue != null) {
            return restrictionValue;
        }
        String type = attrElem.getAttribute("type");
        return getTypeBasedValue(type);
        
    }

    // Generate element value based on type or enumeration
    /**
     * Generate a value for an element based on schema constraints (enumeration, restriction, type).
     * @param schemaElement The XML schema element.
     * @return A valid value for the element.
     */
    public String getElementValue(Element schemaElement) {
        if (schemaElement == null) return "";
        if (!"element".equals(schemaElement.getLocalName())) {
            return "";
        }
        List<String> enums = schemaParser.findEnumerationValues(schemaElement);
        if (!enums.isEmpty()) {
            for (String v : enums) {
                if (v != null && !v.trim().isEmpty()) {
                    return v;
                }
            }
        }
        // Check for restrictions
        String restrictionValue = getValueFromRestrictions(schemaElement);
        if (restrictionValue != null) {
            return restrictionValue;
        }
        String type = schemaElement.getAttribute("type");
        return getTypeBasedValue(type);
        
    }

    /**
     * Generate a value based on restrictions (pattern, min/max length, min/max inclusive, base type).
     * Returns null if no restrictions are present.
     */
    private String getValueFromRestrictions(Element element) {
        // Try to find inline simpleType
        Element simpleType = schemaParser.findChildElement(element, "simpleType");
        Element restriction = null;
        if (simpleType != null) {
            restriction = schemaParser.findChildElement(simpleType, "restriction");
        } else {
            // Try to resolve type definition
            String typeName = element.getAttribute("type");
            if (!typeName.isEmpty()) {
                String resolvedTypeName = typeName.contains(":") ? typeName.split(":")[1] : typeName;
                Element typeDef = schemaParser.resolveTypeDefinition(resolvedTypeName);
                if (typeDef != null && "simpleType".equals(typeDef.getLocalName())) {
                    restriction = schemaParser.findChildElement(typeDef, "restriction");
                }
            }
        }
        if (restriction != null) {
            // Pattern
            Element patternElem = schemaParser.findChildElement(restriction, "pattern");
            if (patternElem != null) {
                String pattern = patternElem.getAttribute("value");
                // Simple common patterns
                if (pattern.matches("[a-zA-Z]+")) return "abc";
                if (pattern.matches("[0-9]+")) return "123";
                if (pattern.contains("[0-9]")) return "123";
                if (pattern.contains("[a-zA-Z]")) return "abc";
                return pattern.replaceAll("[^a-zA-Z0-9]", "");
            }
            // minLength/maxLength
            String minLength = null, maxLength = null;
            NodeList minLengthNodes = restriction.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "minLength");
            if (minLengthNodes.getLength() > 0) {
                minLength = ((Element)minLengthNodes.item(0)).getAttribute("value");
            }
            NodeList maxLengthNodes = restriction.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "maxLength");
            if (maxLengthNodes.getLength() > 0) {
                maxLength = ((Element)maxLengthNodes.item(0)).getAttribute("value");
            }
            if (minLength != null) {
                int len = Integer.parseInt(minLength);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < len; i++) sb.append('a');
                return sb.toString();
            }
            if (maxLength != null) {
                int len = Integer.parseInt(maxLength);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < len; i++) sb.append('b');
                return sb.toString();
            }
            // minInclusive/maxInclusive
            String minInc = null, maxInc = null;
            NodeList minIncNodes = restriction.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "minInclusive");
            if (minIncNodes.getLength() > 0) {
                minInc = ((Element)minIncNodes.item(0)).getAttribute("value");
            }
            NodeList maxIncNodes = restriction.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "maxInclusive");
            if (maxIncNodes.getLength() > 0) {
                maxInc = ((Element)maxIncNodes.item(0)).getAttribute("value");
            }
            if (minInc != null) return minInc;
            if (maxInc != null) return maxInc;
            // fallback for base type
            String base = restriction.getAttribute("base");
            return getTypeBasedValue(base);
        }
        return null;
    }

    /**
     * Generate a value for a given XML schema type (e.g., string, int, date).
     */
    private String getTypeBasedValue(String type) {
        if (type == null) return "";
        type = type.toLowerCase();
        if (type.endsWith("string")) {
            return "abc";
        } else if (type.endsWith("gyear")) {
            return "2025";
        } else if (type.endsWith("date")) {
            return "2025-01-01";
        } else if (type.endsWith("boolean")) {
            return "true";
        } else if (type.endsWith("int") || type.endsWith("integer") || type.endsWith("short") || type.endsWith("byte") || type.endsWith("long")) {
            return "1";
        } else if (type.endsWith("decimal") || type.endsWith("float") || type.endsWith("double")) {
            return "1.23";
        } else {
            return type + "Value";
        }
    }
}