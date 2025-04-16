import java.util.ArrayList;
import java.util.List;

/**
 * ConstraintModel holds the parsed constraints for all elements in the schema.
 */
public class ConstraintModel {
    private List<ElementConstraint> elements = new ArrayList<>();

    public List<ElementConstraint> getElements() {
        return elements;
    }

    public void setElements(List<ElementConstraint> elements) {
        this.elements = elements;
    }

    public void addElementConstraint(ElementConstraint constraint) {
        this.elements.add(constraint);
    }
}
