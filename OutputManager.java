import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Handles writing test cases to disk.
 */
public class OutputManager {
    private String outputDirectory;

    public OutputManager(String outputDirectory) {
        this.outputDirectory = outputDirectory;
        new File(outputDirectory).mkdirs();
    }

    public void saveTestCase(TestCase testCase) {
        try {
            File outFile = new File(outputDirectory, testCase.getName() + ".xml");
            try (FileWriter fw = new FileWriter(outFile)) {
                fw.write(testCase.getXmlContent());
            }
            // Optionally write metadata (description, expected result)
        } catch (IOException e) {
            System.err.println("Error writing test case: " + testCase.getName());
            e.printStackTrace();
        }
    }

    public void saveAllTestCases(List<TestCase> testCases) {
        for (TestCase tc : testCases) {
            saveTestCase(tc);
        }
    }
}
