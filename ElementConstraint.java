import java.util.ArrayList;
import java.util.List;

/**
 * ElementConstraint represents the constraints for a single schema element.
 */
public class ElementConstraint {
    private String name;
    private String type;
    private int minOccurs = 1;
    private int maxOccurs = 1;
    private List<String> enumerationValues = new ArrayList<>();
    // TODO: Add more constraints (pattern, length, etc.)

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getMinOccurs() { return minOccurs; }
    public void setMinOccurs(int minOccurs) { this.minOccurs = minOccurs; }

    public int getMaxOccurs() { return maxOccurs; }
    public void setMaxOccurs(int maxOccurs) { this.maxOccurs = maxOccurs; }

    public List<String> getEnumerationValues() { return enumerationValues; }
    public void setEnumerationValues(List<String> enumerationValues) { this.enumerationValues = enumerationValues; }

    public void addEnumerationValue(String value) { this.enumerationValues.add(value); }
}
