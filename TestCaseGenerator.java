import java.util.ArrayList;
import java.util.List;

/**
 * Generates positive and negative test cases from a ConstraintModel.
 */
public class TestCaseGenerator {
    // Generate positive test cases that should pass validation
    public List<TestCase> generatePositiveTestCases(ConstraintModel model) {
        List<TestCase> testCases = new ArrayList<>();
        for (ElementConstraint ec : model.getElements()) {
            // For each enumeration value, generate a positive test
            for (String enumVal : ec.getEnumerationValues()) {
                TestCase tc = new TestCase();
                tc.setName(ec.getName() + "_enum_" + enumVal);
                tc.setDescription("Valid enum value for " + ec.getName());
                tc.setXmlContent("<!-- TODO: generate XML instance with value '" + enumVal + "' for element '" + ec.getName() + "' -->");
                tc.setExpectValid(true);
                testCases.add(tc);
            }
        }
        return testCases;
    }
    // Generate negative test cases that should fail validation
    public List<TestCase> generateNegativeTestCases(ConstraintModel model) {
        List<TestCase> testCases = new ArrayList<>();
        for (ElementConstraint ec : model.getElements()) {
            if (!ec.getEnumerationValues().isEmpty()) {
                TestCase tc = new TestCase();
                tc.setName(ec.getName() + "_enum_invalid");
                tc.setDescription("Invalid enum value for " + ec.getName());
                tc.setXmlContent("<!-- TODO: generate XML instance with INVALID value for element '" + ec.getName() + "' -->");
                tc.setExpectValid(false);
                tc.setFailureReason("Value not in enumeration");
                testCases.add(tc);
            }
        }
        return testCases;
    }
}
