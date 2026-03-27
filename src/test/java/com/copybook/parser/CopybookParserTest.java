package com.copybook.parser;

import com.copybook.parser.model.CobolCopybook;
import com.copybook.parser.model.CobolDataItem;
import com.copybook.parser.model.CobolUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CopybookParserTest {

    private CopybookParser parser;

    @BeforeEach
    void setUp() {
        parser = new CopybookParser();
    }

    @Test
    void shouldParseCustomerRecordWithAllFeatures() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        assertThat(copybook.getRecords()).hasSize(1);

        CobolDataItem record = copybook.getRecords().get(0);
        assertThat(record.getName()).isEqualTo("CUSTOMER-REC");
        assertThat(record.getLevel()).isEqualTo(1);
        assertThat(record.isGroup()).isTrue();

        // Check children count (excluding level 88s which are on parent)
        assertThat(record.getChildren()).hasSizeGreaterThanOrEqualTo(7);
    }

    @Test
    void shouldParseCustomerId() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        CobolDataItem custId = copybook.getRecords().get(0).getChildren().get(0);
        assertThat(custId.getName()).isEqualTo("CUSTOMER-ID");
        assertThat(custId.getLevel()).isEqualTo(5);
        assertThat(custId.getPic()).isEqualTo("9(9)");
        assertThat(custId.isNumeric()).isTrue();
        assertThat(custId.isSigned()).isFalse();
        assertThat(custId.getIntegerDigits()).isEqualTo(9);
        assertThat(custId.getUsage()).isEqualTo(CobolUsage.DISPLAY);
    }

    @Test
    void shouldParseAlphanumericField() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        CobolDataItem custName = copybook.getRecords().get(0).getChildren().get(1);
        assertThat(custName.getName()).isEqualTo("CUSTOMER-NAME");
        assertThat(custName.getPic()).isEqualTo("X(30)");
        assertThat(custName.isAlphanumeric()).isTrue();
        assertThat(custName.getAlphanumericLength()).isEqualTo(30);
    }

    @Test
    void shouldParseLevel88Conditions() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        CobolDataItem custStatus = copybook.getRecords().get(0).getChildren().get(2);
        assertThat(custStatus.getName()).isEqualTo("CUSTOMER-STATUS");
        assertThat(custStatus.getConditions()).hasSize(2);
        assertThat(custStatus.getConditions().get(0).getName()).isEqualTo("STATUS-ACTIVE");
        assertThat(custStatus.getConditions().get(0).getValues()).containsExactly("A");
        assertThat(custStatus.getConditions().get(1).getName()).isEqualTo("STATUS-INACTIVE");
        assertThat(custStatus.getConditions().get(1).getValues()).containsExactly("I");
    }

    @Test
    void shouldParseSignedPackedDecimal() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        CobolDataItem monthlyLimit = copybook.getRecords().get(0).getChildren().get(3);
        assertThat(monthlyLimit.getName()).isEqualTo("MONTHLY-LIMIT");
        assertThat(monthlyLimit.getPic()).isEqualTo("S9(7)V99");
        assertThat(monthlyLimit.isSigned()).isTrue();
        assertThat(monthlyLimit.getUsage()).isEqualTo(CobolUsage.COMP_3);
        assertThat(monthlyLimit.getIntegerDigits()).isEqualTo(7);
        assertThat(monthlyLimit.getDecimalDigits()).isEqualTo(2);
        assertThat(monthlyLimit.hasDecimal()).isTrue();
    }

    @Test
    void shouldParseFixedOccursGroup() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        CobolDataItem phoneEntry = copybook.getRecords().get(0).getChildren().get(4);
        assertThat(phoneEntry.getName()).isEqualTo("PHONE-ENTRY");
        assertThat(phoneEntry.hasOccurs()).isTrue();
        assertThat(phoneEntry.getOccursMin()).isEqualTo(3);
        assertThat(phoneEntry.getOccursMax()).isEqualTo(3);
        assertThat(phoneEntry.isGroup()).isTrue();
        assertThat(phoneEntry.getChildren()).hasSize(1);
    }

    @Test
    void shouldParseOccursDependingOn() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        CobolDataItem itemCount = copybook.getRecords().get(0).getChildren().get(5);
        assertThat(itemCount.getName()).isEqualTo("ITEM-COUNT");
        assertThat(itemCount.getUsage()).isEqualTo(CobolUsage.COMP);

        CobolDataItem itemDetail = copybook.getRecords().get(0).getChildren().get(6);
        assertThat(itemDetail.getName()).isEqualTo("ITEM-DETAIL");
        assertThat(itemDetail.hasOccurs()).isTrue();
        assertThat(itemDetail.getOccursMin()).isEqualTo(0);
        assertThat(itemDetail.getOccursMax()).isEqualTo(10);
        assertThat(itemDetail.getDependingOn()).isEqualTo("ITEM-COUNT");
    }

    @Test
    void shouldParseMultipleRecords() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/multi-record.cpy"));

        assertThat(copybook.getRecords()).hasSize(3);
        assertThat(copybook.hasMultipleRecords()).isTrue();

        assertThat(copybook.getRecords().get(0).getName()).isEqualTo("CUSTOMER-REC");
        assertThat(copybook.getRecords().get(1).getName()).isEqualTo("VENDOR-REC");
        assertThat(copybook.getRecords().get(2).getName()).isEqualTo("ORDER-REC");
    }

    @Test
    void shouldParseRedefines() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/redefines-test.cpy"));

        CobolDataItem record = copybook.getRecords().get(0);
        assertThat(record.getName()).isEqualTo("PAYMENT-REC");

        // Find the REDEFINES field
        CobolDataItem rawField = record.getChildren().stream()
                .filter(c -> "CUSTOMER-DATA-RAW".equals(c.getName()))
                .findFirst().orElse(null);

        assertThat(rawField).isNotNull();
        assertThat(rawField.hasRedefines()).isTrue();
        assertThat(rawField.getRedefines()).isEqualTo("CUSTOMER-DATA");
    }

    @Test
    void shouldParseComp1AndComp2() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/redefines-test.cpy"));

        CobolDataItem record = copybook.getRecords().get(0);

        CobolDataItem interestFactor = record.getChildren().stream()
                .filter(c -> "INTEREST-FACTOR".equals(c.getName()))
                .findFirst().orElse(null);

        assertThat(interestFactor).isNotNull();
        assertThat(interestFactor.getUsage()).isEqualTo(CobolUsage.COMP_1);

        CobolDataItem highPrecision = record.getChildren().stream()
                .filter(c -> "HIGH-PRECISION-RATE".equals(c.getName()))
                .findFirst().orElse(null);

        assertThat(highPrecision).isNotNull();
        assertThat(highPrecision.getUsage()).isEqualTo(CobolUsage.COMP_2);
    }

    @Test
    void shouldParseLevel77AsStandalone() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/redefines-test.cpy"));

        assertThat(copybook.getStandaloneItems()).hasSize(1);
        assertThat(copybook.getStandaloneItems().get(0).getName()).isEqualTo("GLOBAL-COUNTER");
        assertThat(copybook.getStandaloneItems().get(0).isLevel77()).isTrue();
    }

    @Test
    void shouldDetectFiller() throws IOException {
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/redefines-test.cpy"));

        CobolDataItem record = copybook.getRecords().get(0);

        boolean hasFiller = record.getChildren().stream().anyMatch(CobolDataItem::isFiller);
        assertThat(hasFiller).isTrue();
    }
}
