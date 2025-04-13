import org.w3c.dom.Element;
import java.util.List;

public class XmlValueHelper {
    private final SchemaParser schemaParser;

    public XmlValueHelper(SchemaParser schemaParser) {
        this.schemaParser = schemaParser;
    }

    // Generate attribute value based on type or enumeration
    public String getAttributeValue(Element attrElem) {
        List<String> attrEnums = schemaParser.findEnumerationValues(attrElem);
        if (!attrEnums.isEmpty()) {
            return attrEnums.get(0);
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
        List<String> enums = schemaParser.findEnumerationValues(schemaElement);
        if (!enums.isEmpty()) {
            return enums.get(0);
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