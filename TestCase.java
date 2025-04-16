/**
 * TestCase represents a single generated XML test instance.
 */
public class TestCase {
    private String name;
    private String description;
    private String xmlContent;
    private boolean expectValid;
    private String failureReason;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getXmlContent() { return xmlContent; }
    public void setXmlContent(String xmlContent) { this.xmlContent = xmlContent; }

    public boolean isExpectValid() { return expectValid; }
    public void setExpectValid(boolean expectValid) { this.expectValid = expectValid; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
