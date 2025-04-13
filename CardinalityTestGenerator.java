import java.util.List;
import org.w3c.dom.Element;

/**
 * Class for generating cardinality test cases
 */
public class CardinalityTestGenerator {
    
    private XMLSchemaTestGenerator generator;
    private TestXmlGenerator xmlGenerator;
    
    public CardinalityTestGenerator(XMLSchemaTestGenerator generator) {
        this.generator = generator;
        this.xmlGenerator = new TestXmlGenerator(generator);
    }
    
    /**
     * Generate tests for cardinality constraints
     */
    public void generateCardinalityTests(String elementName, Element element, 
                                       String targetNamespace, String schemaFile) throws Exception {
        // Get child elements for this element
        List<ElementInfo> childElements = generator.getGlobalElementsMap().get(elementName);
        if (childElements == null || childElements.isEmpty()) {
            return;
        }
        
        for (ElementInfo childInfo : childElements) {
            String childName = childInfo.name;
            int minOccurs = childInfo.minOccurs;
            int maxOccurs = childInfo.maxOccurs;
            boolean isReference = childInfo.isReference;
            
            // Extract local name if it's a qualified name with prefix
            String localChildName = childName;
            String prefix = "";
            if (childName.contains(":")) {
                String[] parts = childName.split(":");
                prefix = parts[0];
                localChildName = parts[1];
            }
            
            // Test file base name
            String baseFileName = elementName + "_" + localChildName;
            
            // Generate positive tests
            
            // Min occurs test
            if (minOccurs > 0) {
                String testCase = "min";
                String fileName = "test-output/positive/cardinality/" + baseFileName + "_" + testCase + ".xml";
                String xml = xmlGenerator.generateTestXml(elementName, childElements, childName, minOccurs, isReference, targetNamespace);
                generator.writeTestFile(fileName, xml);
                generator.validateAgainstSchema(fileName, schemaFile, true);
            }
            
            // Max occurs test (if not unbounded)
            if (maxOccurs != Integer.MAX_VALUE) {
                String testCase = "max";
                String fileName = "test-output/positive/cardinality/" + baseFileName + "_" + testCase + ".xml";
                String xml = xmlGenerator.generateTestXml(elementName, childElements, childName, maxOccurs, isReference, targetNamespace);
                generator.writeTestFile(fileName, xml);
                generator.validateAgainstSchema(fileName, schemaFile, true);
            }
            
            // Between min and max (if different)
            if (minOccurs < maxOccurs && maxOccurs != Integer.MAX_VALUE && maxOccurs - minOccurs > 1) {
                int middle = minOccurs + (maxOccurs - minOccurs) / 2;
                String testCase = "between";
                String fileName = "test-output/positive/cardinality/" + baseFileName + "_" + testCase + ".xml";
                String xml = xmlGenerator.generateTestXml(elementName, childElements, childName, middle, isReference, targetNamespace);
                generator.writeTestFile(fileName, xml);
                generator.validateAgainstSchema(fileName, schemaFile, true);
            }
            
            // Generate negative tests
            
            // Less than min occurs (if min > 0)
            if (minOccurs > 0) {
                String testCase = "lessThanMin";
                String fileName = "test-output/negative/cardinality/" + baseFileName + "_" + testCase + ".xml";
                String xml = xmlGenerator.generateTestXml(elementName, childElements, childName, minOccurs - 1, isReference, targetNamespace);
                generator.writeTestFile(fileName, xml);
                generator.validateAgainstSchema(fileName, schemaFile, false);
            }
            
            // More than max occurs (if not unbounded)
            if (maxOccurs != Integer.MAX_VALUE) {
                String testCase = "moreThanMax";
                String fileName = "test-output/negative/cardinality/" + baseFileName + "_" + testCase + ".xml";
                String xml = xmlGenerator.generateTestXml(elementName, childElements, childName, maxOccurs + 1, isReference, targetNamespace);
                generator.writeTestFile(fileName, xml);
                generator.validateAgainstSchema(fileName, schemaFile, false);
            }
        }
    }
}