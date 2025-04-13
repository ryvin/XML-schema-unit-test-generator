# XML Schema Test Generator

A Java-based tool that automatically generates positive and negative test cases for XML Schema validation based on cardinality and enumeration constraints.

## Overview

XML Schema Test Generator analyzes XML Schema files (XSD) and creates test XML files to validate against the schema. It generates both valid (positive) and invalid (negative) test cases to help ensure your XML processing code handles both compliant and non-compliant XML correctly.

The generator examines your schema's constraints and creates meaningful test scenarios that verify both the expected behavior (positive tests) and error handling (negative tests).

## Key Features

- **Automated Test Generation**: Eliminates manual test case creation for XML schemas
- **Comprehensive Constraint Testing**:
  - Cardinality constraints (`minOccurs`/`maxOccurs`)
  - Enumeration validation (fixed value sets)
- **Namespace Support**: Correctly handles XML namespaces in generated test files
- **Schema Resolution**: Processes included and imported schemas
- **Accurate Reference Resolution**: All required children for referenced elements are now included in generated XML, ensuring valid test files for schemas using `<element ref="...">`.
- **Validation Verification**: Tests generated files against the schema
- **Organized Output**: Categorizes test files by type and expected result

## Requirements

- Java 8 or higher
- All necessary XML libraries are included in standard Java SE

## Project Structure

The test generator uses a modular architecture for better maintainability:

| File | Description |
|------|-------------|
| `XMLSchemaTestGenerator.java` | Main class orchestrating the test generation process |
| `ElementInfo.java` | Data class for storing element information |
| `SchemaParser.java` | Handles XML schema parsing and element extraction |
| `TestXmlGenerator.java` | Generates XML test files based on schema constraints |
| `CardinalityTestGenerator.java` | Generates tests for cardinality constraints |
| `EnumerationTestGenerator.java` | Generates tests for enumeration constraints |
| `XmlValueHelper.java` | Utility for generating attribute and element values based on schema type or enumeration |

## Installation

1. Clone or download this repository
2. Compile the Java files:
   ```
   javac *.java
   ```

## Usage

Run the generator with your XSD file as an argument:

```
java XMLSchemaTestGenerator your-schema.xsd
```

### Output Structure

Test files are generated in the following directories:

```
test-output/
├── positive/
│   ├── cardinality/   # Valid cardinality test cases
│   └── enumeration/   # Valid enumeration test cases
└── negative/
    ├── cardinality/   # Invalid cardinality test cases
    └── enumeration/   # Invalid enumeration test cases
```

Each test file is named according to the element and constraint being tested.

## Example Test Cases

### Cardinality Tests

For an element with constraints `minOccurs="1"` and `maxOccurs="5"`, the generator creates:

- **Positive Tests**:
  - `element_min.xml`: Contains exactly the minimum number of occurrences (1)
  - `element_max.xml`: Contains exactly the maximum number of occurrences (5)
  - `element_between.xml`: Contains a value between min and max (3)
  
- **Negative Tests**:
  - `element_lessThanMin.xml`: Contains fewer than the minimum (0)
  - `element_moreThanMax.xml`: Contains more than the maximum (6)

### Enumeration Tests

For an element with an enumeration constraint (`sedan`, `suv`, `hatchback`), the generator creates:

- **Positive Tests**:
  - `element_type_sedan.xml`: Contains the value "sedan"
  - `element_type_suv.xml`: Contains the value "suv"
  - `element_type_hatchback.xml`: Contains the value "hatchback"
  
- **Negative Tests**:
  - `element_type_invalid.xml`: Contains an invalid value

## Example Schema

The generator works with schemas like this vehicle schema:

```xml
<xs:schema
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:vh="http://example.com/vehicles"
  targetNamespace="http://example.com/vehicles"
  elementFormDefault="qualified">

  <xs:element name="vehicles">
    <xs:complexType>
      <xs:sequence>
        <xs:element ref="vh:cars" />
        <xs:element ref="vh:bikes" />
      </xs:sequence>
    </xs:complexType>
  </xs:element>
</xs:schema>
```

## Customization

You can extend the code to support additional XML Schema constraints by:

1. Creating new generator classes for specific constraint types
2. Updating the SchemaParser to detect the new constraints
3. Modifying the main XMLSchemaTestGenerator to utilize the new generators

## Limitations

- Limited support for complex type inheritance
- Does not handle all XML Schema facets (only cardinality and enumerations)
- External schema resolution is simplified
- No support for wildcards (`xs:any` and `xs:anyAttribute`)
- Complex substitution groups not fully supported

## Troubleshooting

If generated test files fail validation unexpectedly, check:

1. Namespace declarations in your schema
2. References to external schemas
3. Complex type definitions
4. Custom type definitions

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is available under the MIT License.