import java.io.File;
import java.util.List;

/**
 * Main entry point for ConstraintCrafter.
 * Orchestrates schema parsing, test generation, output, and (optional) validation.
 */
public class ConstraintCrafter {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java ConstraintCrafter <schema.xsd> <outputDir>");
            System.exit(1);
        }
        File xsdFile = new File(args[0]);
        String outputDir = args[1];

        // Parse schema
        SchemaParser parser = new SchemaParser();
        ConstraintModel model = parser.parseSchema(xsdFile);

        // Generate test cases
        TestCaseGenerator generator = new TestCaseGenerator();
        List<TestCase> positiveTests = generator.generatePositiveTestCases(model);
        List<TestCase> negativeTests = generator.generateNegativeTestCases(model);

        System.out.println("[ConstraintCrafter] Positive test cases: " + positiveTests.size());
        System.out.println("[ConstraintCrafter] Negative test cases: " + negativeTests.size());
        System.out.println("[ConstraintCrafter] Output directory: " + outputDir);
        if (positiveTests.isEmpty() && negativeTests.isEmpty()) {
            System.out.println("[ConstraintCrafter] WARNING: No test cases generated. Check your schema or parsing logic.");
        }

        // Save test cases
        OutputManager outputManager = new OutputManager(outputDir);
        outputManager.saveAllTestCases(positiveTests);
        outputManager.saveAllTestCases(negativeTests);

        // (Optional) Validation step can be added here
        System.out.println("Test case generation complete. See output in: " + outputDir);
    }
}
