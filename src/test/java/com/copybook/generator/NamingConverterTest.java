package com.copybook.generator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NamingConverterTest {

    @Test
    void shouldConvertToCamelCase() {
        assertThat(NamingConverter.toCamelCase("CUSTOMER-NUMBER")).isEqualTo("customerNumber");
        assertThat(NamingConverter.toCamelCase("CUST-ID")).isEqualTo("custId");
        assertThat(NamingConverter.toCamelCase("ITEM-DETAIL")).isEqualTo("itemDetail");
        assertThat(NamingConverter.toCamelCase("PHONE-NUMBER")).isEqualTo("phoneNumber");
        assertThat(NamingConverter.toCamelCase("MONTHLY-LIMIT")).isEqualTo("monthlyLimit");
    }

    @Test
    void shouldConvertToPascalCase() {
        assertThat(NamingConverter.toPascalCase("CUSTOMER-REC")).isEqualTo("CustomerRec");
        assertThat(NamingConverter.toPascalCase("ORDER-REC")).isEqualTo("OrderRec");
        assertThat(NamingConverter.toPascalCase("ADDRESS-GROUP")).isEqualTo("AddressGroup");
        assertThat(NamingConverter.toPascalCase("ITEM-DETAIL")).isEqualTo("ItemDetail");
    }

    @Test
    void shouldConvertToEnumConstant() {
        assertThat(NamingConverter.toEnumConstant("STATUS-ACTIVE", "STATUS-CODE"))
                .isEqualTo("ACTIVE");
        assertThat(NamingConverter.toEnumConstant("STATUS-INACTIVE", "STATUS-CODE"))
                .isEqualTo("INACTIVE");
        assertThat(NamingConverter.toEnumConstant("PAY-CREDIT", "PAYMENT-TYPE"))
                .isEqualTo("PAY_CREDIT");
    }

    @Test
    void shouldSanitizeJavaKeywords() {
        assertThat(NamingConverter.toCamelCase("CLASS")).isEqualTo("classField");
        assertThat(NamingConverter.toCamelCase("RETURN")).isEqualTo("returnField");
        assertThat(NamingConverter.toCamelCase("INT")).isEqualTo("intField");
    }

    @Test
    void shouldHandleSingleWordNames() {
        assertThat(NamingConverter.toCamelCase("AMOUNT")).isEqualTo("amount");
        assertThat(NamingConverter.toPascalCase("AMOUNT")).isEqualTo("Amount");
    }
}
