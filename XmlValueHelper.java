import org.w3c.dom.Element;
import java.util.List;

public class XmlValueHelper {
    private final SchemaParser schemaParser;

    public XmlValueHelper(SchemaParser schemaParser) {
        this.schemaParser = schemaParser;
    }

    // Generate attribute value based on type or enumeration
    public String getAttributeValue(Element attrElem) {
        // Only use enumerations if this is an xs:attribute node (not element)
        if (!"attribute".equals(attrElem.getLocalName())) {
            return "SampleValue";
        }
        List<String> attrEnums = schemaParser.findEnumerationValues(attrElem);
        System.out.println("[DEBUG] getAttributeValue: nodeName=" + attrElem.getNodeName() +
            ", localName=" + attrElem.getLocalName() +
            ", enums=" + attrEnums);
        // Filter out empty or whitespace-only values
        String validEnum = null;
        for (String v : attrEnums) {
            if (v != null && !v.trim().isEmpty()) {
                validEnum = v;
                break;
            }
        }
        if (validEnum != null) {
            return validEnum;
        }
        String type = attrElem.getAttribute("type");
        if (type.endsWith("string")) {
            return "SampleString";
        } else if (type.endsWith("gYear")) {
            return "2020";
        } else if (type.endsWith("int") || type.endsWith("integer")) {
            return "1";
        } else {
            return "SampleValue";
        }
    }

    // Generate element value based on type or enumeration
    public String getElementValue(Element schemaElement) {
        if (schemaElement == null) return "SampleValue";
        // Only use enumerations if this is an xs:element node (not attribute)
        if (!"element".equals(schemaElement.getLocalName())) {
            return "SampleValue";
        }
        List<String> enums = schemaParser.findEnumerationValues(schemaElement);
        System.out.println("[DEBUG] getElementValue: nodeName=" + schemaElement.getNodeName() +
            ", localName=" + schemaElement.getLocalName() +
            ", name=" + schemaElement.getAttribute("name") +
            ", enums=" + enums);
        // Filter out empty or whitespace-only values
        String validEnum = null;
        for (String v : enums) {
            if (v != null && !v.trim().isEmpty()) {
                validEnum = v;
                break;
            }
        }
        if (validEnum != null) {
            return validEnum;
        }
        String type = schemaElement.getAttribute("type");
        if (type.endsWith("string")) {
            return "SampleString";
        } else if (type.endsWith("gYear")) {
            return "2020";
        } else if (type.endsWith("int") || type.endsWith("integer")) {
            return "1";
        } else {
            return "SampleValue";
        }
    }
}