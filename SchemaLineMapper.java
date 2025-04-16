import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility to map element/type names to line numbers in an XSD schema file.
 */
public class SchemaLineMapper {
    private final Map<String, Integer> elementLineMap = new HashMap<>();
    private final Map<String, Integer> typeLineMap = new HashMap<>();
    private final String schemaFile;

    public SchemaLineMapper(String schemaFile) {
        this.schemaFile = schemaFile;
        buildLineMaps();
    }

    private void buildLineMaps() {
        try (BufferedReader reader = new BufferedReader(new FileReader(schemaFile))) {
            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                // Match <xs:element name="...">
                if (line.contains("<xs:element") && line.contains("name=")) {
                    String name = extractName(line);
                    if (name != null) {
                        elementLineMap.put(name, lineNum);
                    }
                }
                // Match <xs:simpleType name="..."> or <xs:complexType name="...">
                if ((line.contains("<xs:simpleType") || line.contains("<xs:complexType")) && line.contains("name=")) {
                    String name = extractName(line);
                    if (name != null) {
                        typeLineMap.put(name, lineNum);
                    }
                }
                lineNum++;
            }
        } catch (Exception e) {
            // Fallback: ignore
        }
    }

    private String extractName(String line) {
        int idx = line.indexOf("name=");
        if (idx != -1) {
            int start = line.indexOf('"', idx);
            int end = line.indexOf('"', start + 1);
            if (start != -1 && end != -1) {
                return line.substring(start + 1, end);
            }
        }
        return null;
    }

    public Integer getElementLine(String name) {
        return elementLineMap.get(name);
    }
    public Integer getTypeLine(String name) {
        return typeLineMap.get(name);
    }
}
