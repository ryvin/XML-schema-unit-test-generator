/**
 * Helper class to store element information
 */
public class ElementInfo {
    String name;
    boolean isReference;
    int minOccurs;
    int maxOccurs;
    boolean isSimpleType; // true if the element is a simple type (can have text content)
}
