/**
 * File: src/main/java/XmlValueHelper.java
 * 
 * Description: Helper class for generating XML values based on XML Schema datatypes.
 * Provides support for all built-in XML Schema simple types including string, numeric,
 * date/time, binary, and URI-related types. Handles enumeration constraints and
 * generates appropriate test values for each type.
 */
import org.w3c.dom.Element;
import java.util.List;
import java.util.Random;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Base64;
import javax.xml.XMLConstants;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.math.BigDecimal;
import java.math.BigInteger;

public class XmlValueHelper {
    private static final Logger logger = Logger.getLogger(XmlValueHelper.class.getName());
    private final SchemaParser schemaParser;
    private final Random random = new Random();

    // Collections of XSD built-in types from the schema specification
    private static final Set<String> STRING_TYPES = new HashSet<>(Arrays.asList(
        "string", "normalizedString", "token", "language", "Name", "NCName", "ID", 
        "IDREF", "IDREFS", "ENTITY", "ENTITIES", "NMTOKEN", "NMTOKENS"
    ));
    
    private static final Set<String> NUMERIC_INTEGER_TYPES = new HashSet<>(Arrays.asList(
        "integer", "positiveInteger", "negativeInteger", "nonNegativeInteger", 
        "nonPositiveInteger", "long", "int", "short", "byte", "unsignedLong", 
        "unsignedInt", "unsignedShort", "unsignedByte"
    ));
    
    private static final Set<String> NUMERIC_DECIMAL_TYPES = new HashSet<>(Arrays.asList(
        "decimal"
    ));
    
    private static final Set<String> FLOAT_TYPES = new HashSet<>(Arrays.asList(
        "float", "double"
    ));
    
    private static final Set<String> DATE_TIME_TYPES = new HashSet<>(Arrays.asList(
        "date", "time", "dateTime", "duration", "gYearMonth", "gYear", "gMonthDay", 
        "gDay", "gMonth"
    ));
    
    private static final Set<String> BINARY_TYPES = new HashSet<>(Arrays.asList(
        "hexBinary", "base64Binary"
    ));
    
    private static final Set<String> BOOLEAN_TYPES = new HashSet<>(Arrays.asList(
        "boolean"
    ));
    
    private static final Set<String> URI_TYPES = new HashSet<>(Arrays.asList(
        "anyURI", "QName", "NOTATION"
    ));

    public XmlValueHelper(SchemaParser schemaParser) {
        this.schemaParser = schemaParser;
    }

    /**
     * Generate attribute value based on type or enumeration
     */
    public String getAttributeValue(Element attrElem) {
        // Only use enumerations if this is an xs:attribute node
        if (!"attribute".equals(attrElem.getLocalName())) {
            logger.log(Level.FINE, "Not an attribute element: {0}, returning default value", 
                attrElem.getLocalName());
            return "SampleValue";
        }
        
        // First check for enumerations
        List<String> attrEnums = schemaParser.findEnumerationValues(attrElem);
        logger.log(Level.FINE, "Attribute enumerations for {0}: {1}", 
            new Object[]{attrElem.getAttribute("name"), attrEnums});
        
        // Filter out empty or whitespace-only values
        String validEnum = getFirstValidValue(attrEnums);
        if (validEnum != null) {
            logger.log(Level.FINE, "Using enumeration value for attribute {0}: {1}", 
                new Object[]{attrElem.getAttribute("name"), validEnum});
            return validEnum;
        }
        
        // If no valid enumeration, generate based on type
        String type = attrElem.getAttribute("type");
        logger.log(Level.FINE, "Generating value for attribute {0} based on type {1}", 
            new Object[]{attrElem.getAttribute("name"), type});
        return generateValueForType(type);
    }

    /**
     * Generate element value based on type or enumeration
     */
    public String getElementValue(Element schemaElement) {
        if (schemaElement == null) {
            logger.log(Level.WARNING, "Null schema element, returning default value");
            return "SampleValue";
        }
        
        // Only use enumerations if this is an xs:element node
        if (!"element".equals(schemaElement.getLocalName())) {
            logger.log(Level.FINE, "Not an element: {0}, returning default value", 
                schemaElement.getLocalName());
            return "SampleValue";
        }
        
        // First check for enumerations
        List<String> enums = schemaParser.findEnumerationValues(schemaElement);
        logger.log(Level.FINE, "Element enumerations for {0}: {1}", 
            new Object[]{schemaElement.getAttribute("name"), enums});
        
        // Filter out empty or whitespace-only values
        String validEnum = getFirstValidValue(enums);
        if (validEnum != null) {
            logger.log(Level.FINE, "Using enumeration value for element {0}: {1}", 
                new Object[]{schemaElement.getAttribute("name"), validEnum});
            return validEnum;
        }
        
        // If no valid enumeration, generate based on type
        String type = schemaElement.getAttribute("type");
        logger.log(Level.FINE, "Generating value for element {0} based on type {1}", 
            new Object[]{schemaElement.getAttribute("name"), type});
        return generateValueForType(type);
    }
    
    /**
     * Get the first non-empty value from a list
     */
    private String getFirstValidValue(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v;
            }
        }
        
        return null;
    }
    
    /**
     * Generate value for specific XSD type
     */
    public String generateValueForType(String type) {
        // Handle empty type
        if (type == null || type.isEmpty()) {
            logger.log(Level.FINE, "Empty type, returning default value");
            return "SampleValue";
        }
        
        // Remove namespace prefix if present
        String typeLocal = type;
        if (type.contains(":")) {
            typeLocal = type.substring(type.indexOf(":") + 1);
        }
        
        // Generate appropriate value based on type
        try {
            if (STRING_TYPES.contains(typeLocal)) {
                return generateStringValue(typeLocal);
            } else if (NUMERIC_INTEGER_TYPES.contains(typeLocal)) {
                return generateIntegerValue(typeLocal);
            } else if (NUMERIC_DECIMAL_TYPES.contains(typeLocal)) {
                return generateDecimalValue(typeLocal);
            } else if (FLOAT_TYPES.contains(typeLocal)) {
                return generateFloatValue(typeLocal);
            } else if (DATE_TIME_TYPES.contains(typeLocal)) {
                return generateDateTimeValue(typeLocal);
            } else if (BINARY_TYPES.contains(typeLocal)) {
                return generateBinaryValue(typeLocal);
            } else if (BOOLEAN_TYPES.contains(typeLocal)) {
                return generateBooleanValue();
            } else if (URI_TYPES.contains(typeLocal)) {
                return generateURIValue(typeLocal);
            } else {
                logger.log(Level.INFO, "Unknown type {0}, returning default value", typeLocal);
                return "SampleValue";
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error generating value for type {0}: {1}", 
                new Object[]{typeLocal, e.getMessage()});
            return "SampleValue";
        }
    }
    
    /**
     * Generate string value based on specific string type
     */
    private String generateStringValue(String type) {
        logger.log(Level.FINE, "Generating string value for type: {0}", type);
        
        // Different string types have different constraints
        if ("language".equals(type)) {
            // ISO 639 language codes
            String[] langs = {"en", "fr", "de", "es", "it", "ja", "zh", "ru"};
            return langs[random.nextInt(langs.length)];
        } else if (type.contains("NMTOKEN")) {
            // Name tokens - no spaces
            return "Token" + random.nextInt(1000);
        } else if (type.contains("Name") || type.equals("NCName") || type.equals("ID") || 
                  type.equals("IDREF") || type.equals("ENTITY")) {
            // XML Names - must start with letter or underscore
            char first = (random.nextBoolean()) ? 
                         (char)('a' + random.nextInt(26)) : '_';
            return first + "Name" + random.nextInt(1000);
        } else {
            // Generic string
            return "SampleString" + (random.nextInt(1000) > 500 ? "" : random.nextInt(1000));
        }
    }
    
    /**
     * Generate integer value based on specific integer type
     */
    private String generateIntegerValue(String type) {
        logger.log(Level.FINE, "Generating integer value for type: {0}", type);
        
        switch (type) {
            case "positiveInteger":
                return Integer.toString(1 + random.nextInt(1000));
            case "negativeInteger":
                return Integer.toString(-(1 + random.nextInt(1000)));
            case "nonPositiveInteger":
                return Integer.toString(-(random.nextInt(1000)));
            case "nonNegativeInteger":
                return Integer.toString(random.nextInt(1000));
            case "byte":
                return Integer.toString(random.nextInt(127));
            case "unsignedByte":
                return Integer.toString(random.nextInt(255));
            case "short":
                return Integer.toString(random.nextInt(32767));
            case "unsignedShort":
                return Integer.toString(random.nextInt(65535));
            case "int":
                return Integer.toString(random.nextInt(2147483647));
            case "unsignedInt":
                return Long.toString(random.nextInt(1000000000));
            case "long":
            case "unsignedLong":
            case "integer":
            default:
                return Integer.toString(1 + random.nextInt(100000));
        }
    }
    
    /**
     * Generate decimal value
     */
    private String generateDecimalValue(String type) {
        logger.log(Level.FINE, "Generating decimal value for type: {0}", type);
        
        BigDecimal value = new BigDecimal(random.nextInt(10000))
            .divide(new BigDecimal(100));
        return value.toString();
    }
    
    /**
     * Generate float or double value
     */
    private String generateFloatValue(String type) {
        logger.log(Level.FINE, "Generating float value for type: {0}", type);
        
        if ("double".equals(type)) {
            return Double.toString(random.nextDouble() * 100.0);
        } else {
            return Float.toString(random.nextFloat() * 100.0f);
        }
    }
    
    /**
     * Generate date/time value based on type
     */
    private String generateDateTimeValue(String type) {
        logger.log(Level.FINE, "Generating date/time value for type: {0}", type);
        
        LocalDateTime now = LocalDateTime.now();
        
        switch (type) {
            case "date":
                return now.format(DateTimeFormatter.ISO_DATE);
            case "time":
                return now.format(DateTimeFormatter.ISO_TIME);
            case "dateTime":
                return now.format(DateTimeFormatter.ISO_DATE_TIME);
            case "gYearMonth":
                return now.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            case "gYear":
                return now.format(DateTimeFormatter.ofPattern("yyyy"));
            case "gMonthDay":
                return now.format(DateTimeFormatter.ofPattern("--MM-dd"));
            case "gMonth":
                return now.format(DateTimeFormatter.ofPattern("--MM"));
            case "gDay":
                return now.format(DateTimeFormatter.ofPattern("---dd"));
            case "duration":
                return "P" + random.nextInt(100) + "D";
            default:
                return now.format(DateTimeFormatter.ISO_DATE);
        }
    }
    
    /**
     * Generate binary value
     */
    private String generateBinaryValue(String type) {
        logger.log(Level.FINE, "Generating binary value for type: {0}", type);
        
        byte[] bytes = new byte[8];
        random.nextBytes(bytes);
        
        if ("hexBinary".equals(type)) {
            // Convert to hex string
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02X", b));
            }
            return hex.toString();
        } else {
            // base64Binary
            return Base64.getEncoder().encodeToString(bytes);
        }
    }
    
    /**
     * Generate boolean value
     */
    private String generateBooleanValue() {
        logger.log(Level.FINE, "Generating boolean value");
        return random.nextBoolean() ? "true" : "false";
    }
    
    /**
     * Generate URI, QName or NOTATION value
     */
    private String generateURIValue(String type) {
        logger.log(Level.FINE, "Generating URI-related value for type: {0}", type);
        
        if ("anyURI".equals(type)) {
            return "http://example.org/sample/" + random.nextInt(1000);
        } else if ("QName".equals(type)) {
            return "prefix:localName" + random.nextInt(100);
        } else {
            // NOTATION
            return "NOTATION" + random.nextInt(100);
        }
    }
}