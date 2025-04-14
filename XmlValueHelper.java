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
        if (enums != null && !enums.isEmpty()) {
            // Use the first enumeration value that's not empty
            for (String value : enums) {
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
        }
        
        // If no enumeration values, generate based on type
        String type = schemaElement.getAttribute("type");
        return generateValueForType(type);
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
        if (localType.endsWith("string") || localType.endsWith("normalizedString") || localType.endsWith("token")) {
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
            return "2020";
        } else if (localType.endsWith("gMonth")) {
            return "01";
        } else if (localType.endsWith("gDay")) {
            return "01";
        } else if (localType.endsWith("gYearMonth")) {
            return "2020-01";
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
            if (child.getNodeType() == Node.ELEMENT_NODE && 
                localName.equals(child.getLocalName())) {
                return (Element) child;
            }
        }
        return null;
    }
}