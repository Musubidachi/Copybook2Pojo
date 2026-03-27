package com.copybook.generator;

import com.copybook.parser.CopybookParser;
import com.copybook.parser.model.CobolCopybook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JavaPojoGeneratorTest {

    private CopybookParser parser;
    private JavaPojoGenerator generator;

    @BeforeEach
    void setUp() {
        parser = new CopybookParser();
        generator = new JavaPojoGenerator("com.example.generated");
    }

    @Test
    void shouldGenerateSingleRecordClass() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        Map<String, String> files = generator.generate(copybook);

        assertThat(files).containsKey("CustomerRec.java");
        // Should NOT have a wrapper since only one 01-level record
        assertThat(files.keySet().stream().filter(k -> k.contains("Root"))).isEmpty();
    }

    @Test
    void shouldGenerateWrapperForMultipleRecords() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/multi-record.cpy"));

        Map<String, String> files = generator.generate(copybook);

        assertThat(files).containsKey("CustomerRec.java");
        assertThat(files).containsKey("VendorRec.java");
        assertThat(files).containsKey("OrderRec.java");
        assertThat(files).containsKey("MultiRecordRoot.java");
    }

    @Test
    void shouldIncludeLombokAnnotations() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        Map<String, String> files = generator.generate(copybook);
        String source = files.get("CustomerRec.java");

        assertThat(source).contains("@Data");
        assertThat(source).contains("@NoArgsConstructor");
        assertThat(source).contains("@AllArgsConstructor");
        assertThat(source).contains("@Builder");
        assertThat(source).contains("import lombok.Data;");
    }

    @Test
    void shouldGenerateCobolFieldAnnotations() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        Map<String, String> files = generator.generate(copybook);
        String source = files.get("CustomerRec.java");

        assertThat(source).contains("@CobolField(name = \"CUSTOMER-ID\"");
        assertThat(source).contains("@CobolField(name = \"CUSTOMER-NAME\"");
        assertThat(source).contains("@CobolField(name = \"MONTHLY-LIMIT\"");
    }

    @Test
    void shouldGenerateCorrectJavaTypes() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        Map<String, String> files = generator.generate(copybook);
        String source = files.get("CustomerRec.java");

        assertThat(source).contains("private Integer customerId;");
        assertThat(source).contains("private String customerName;");
        assertThat(source).contains("private String customerStatus;");
        assertThat(source).contains("private BigDecimal monthlyLimit;");
    }

    @Test
    void shouldGenerateListForOccurs() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        Map<String, String> files = generator.generate(copybook);
        String source = files.get("CustomerRec.java");

        assertThat(source).contains("private List<PhoneEntry> phoneEntry;");
        assertThat(source).contains("@CobolOccurs(min = 3, max = 3)");
    }

    @Test
    void shouldGenerateOccursDependingOnAnnotation() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        Map<String, String> files = generator.generate(copybook);
        String source = files.get("CustomerRec.java");

        assertThat(source).contains("@CobolOccurs(min = 0, max = 10, dependingOn = \"ITEM-COUNT\")");
        assertThat(source).contains("private List<ItemDetail> itemDetail;");
    }

    @Test
    void shouldGenerateEnumForLevel88() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        Map<String, String> files = generator.generate(copybook);
        String source = files.get("CustomerRec.java");

        assertThat(source).contains("public enum CustomerStatus");
        assertThat(source).contains("ACTIVE(\"A\")");
        assertThat(source).contains("INACTIVE(\"I\")");
    }

    @Test
    void shouldDetectDatePairAndGenerateLocalDate() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        Map<String, String> files = generator.generate(copybook);
        String source = files.get("CustomerRec.java");

        assertThat(source).contains("LocalDate");
        assertThat(source).contains("@CobolDateFormat");
    }

    @Test
    void shouldGenerateRedefinesAnnotation() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/redefines-test.cpy"));

        Map<String, String> files = generator.generate(copybook);
        String source = files.get("PaymentRec.java");

        assertThat(source).contains("@CobolRedefines(target = \"CUSTOMER-DATA\")");
    }

    @Test
    void shouldGenerateComp1AsFloat() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/redefines-test.cpy"));

        Map<String, String> files = generator.generate(copybook);
        String source = files.get("PaymentRec.java");

        assertThat(source).contains("private Float interestFactor;");
        assertThat(source).contains("private Double highPrecisionRate;");
    }

    @Test
    void shouldGenerateStandaloneHolderForLevel77() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/redefines-test.cpy"));

        Map<String, String> files = generator.generate(copybook);

        assertThat(files).containsKey("RedefinesTestStandalone.java");
        String source = files.get("RedefinesTestStandalone.java");
        assertThat(source).contains("globalCounter");
    }

    @Test
    void shouldDetectSharedStructures() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/multi-record.cpy"));

        Map<String, String> files = generator.generate(copybook);

        // AddressGroup should be generated as a shared class
        assertThat(files).containsKey("AddressGroup.java");

        String addressSource = files.get("AddressGroup.java");
        assertThat(addressSource).contains("public class AddressGroup");
        assertThat(addressSource).contains("private String street;");
        assertThat(addressSource).contains("private Integer zipCode;");
    }

    @Test
    void shouldNotGenerateFieldForFiller() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/redefines-test.cpy"));

        Map<String, String> files = generator.generate(copybook);
        String source = files.get("PaymentRec.java");

        // Should not have a FILLER field
        assertThat(source).doesNotContain("private String filler;");
        assertThat(source).doesNotContain("private String fillerField;");
    }

    @Test
    void shouldIncludePackageDeclaration() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        Map<String, String> files = generator.generate(copybook);
        String source = files.get("CustomerRec.java");

        assertThat(source).startsWith("package com.example.generated;");
    }

    @Test
    void shouldGenerateFieldJavadocWithCobolMetadata() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        Map<String, String> files = generator.generate(copybook);
        String source = files.get("CustomerRec.java");

        assertThat(source).contains("* COBOL Name: CUSTOMER-ID");
        assertThat(source).contains("* Level: 05");
        assertThat(source).contains("* PIC: 9(9)");
        assertThat(source).contains("* Usage: DISPLAY");
    }

    @Test
    void shouldGenerateFixedLengthAnnotation() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        Map<String, String> files = generator.generate(copybook);
        String source = files.get("CustomerRec.java");

        assertThat(source).contains("@CobolFixedLength(length = 30, padChar = ' ', trimOnRead = true)");
        assertThat(source).contains("@Size(max = 30)");
    }

    @Test
    void shouldGenerateDigitsAnnotationForDecimals() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        Map<String, String> files = generator.generate(copybook);
        String source = files.get("CustomerRec.java");

        assertThat(source).contains("@Digits(integer = 7, fraction = 2)");
    }
}
