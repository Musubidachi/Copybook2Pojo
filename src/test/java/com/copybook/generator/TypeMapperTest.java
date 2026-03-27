package com.copybook.generator;

import com.copybook.parser.model.CobolDataItem;
import com.copybook.parser.model.CobolUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TypeMapperTest {

    @Test
    void shouldMapPicXToString() {
        CobolDataItem item = createItem("X(30)", CobolUsage.DISPLAY);
        item.setAlphanumericLength(30);
        assertThat(TypeMapper.mapType(item)).isEqualTo("String");
    }

    @Test
    void shouldMapSmallNumericToInteger() {
        CobolDataItem item = createItem("9(5)", CobolUsage.DISPLAY);
        item.setIntegerDigits(5);
        assertThat(TypeMapper.mapType(item)).isEqualTo("Integer");
    }

    @Test
    void shouldMapLargeNumericToLong() {
        CobolDataItem item = createItem("9(15)", CobolUsage.DISPLAY);
        item.setIntegerDigits(15);
        assertThat(TypeMapper.mapType(item)).isEqualTo("Long");
    }

    @Test
    void shouldMapDecimalToBigDecimal() {
        CobolDataItem item = createItem("S9(7)V99", CobolUsage.DISPLAY);
        item.setIntegerDigits(7);
        item.setDecimalDigits(2);
        item.setSigned(true);
        assertThat(TypeMapper.mapType(item)).isEqualTo("BigDecimal");
    }

    @Test
    void shouldMapComp3ToBigDecimal() {
        CobolDataItem item = createItem("9(5)", CobolUsage.COMP_3);
        item.setIntegerDigits(5);
        assertThat(TypeMapper.mapType(item)).isEqualTo("BigDecimal");
    }

    @Test
    void shouldMapComp1ToFloat() {
        CobolDataItem item = new CobolDataItem();
        item.setUsage(CobolUsage.COMP_1);
        assertThat(TypeMapper.mapType(item)).isEqualTo("Float");
    }

    @Test
    void shouldMapComp2ToDouble() {
        CobolDataItem item = new CobolDataItem();
        item.setUsage(CobolUsage.COMP_2);
        assertThat(TypeMapper.mapType(item)).isEqualTo("Double");
    }

    @Test
    void shouldMapSmallCompToShort() {
        CobolDataItem item = createItem("9(2)", CobolUsage.COMP);
        item.setIntegerDigits(2);
        assertThat(TypeMapper.mapType(item)).isEqualTo("Short");
    }

    @Test
    void shouldMapMediumCompToInteger() {
        CobolDataItem item = createItem("9(7)", CobolUsage.COMP);
        item.setIntegerDigits(7);
        assertThat(TypeMapper.mapType(item)).isEqualTo("Integer");
    }

    private CobolDataItem createItem(String pic, CobolUsage usage) {
        CobolDataItem item = new CobolDataItem();
        item.setPic(pic);
        item.setUsage(usage);
        return item;
    }
}
