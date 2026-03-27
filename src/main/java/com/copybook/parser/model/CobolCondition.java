package com.copybook.parser.model;

import java.util.List;

/**
 * Represents a COBOL level-88 condition name and its associated values.
 */
public class CobolCondition {

    private String name;
    private List<String> values;
    private String throughValue;

    public CobolCondition() {
    }

    public CobolCondition(String name, List<String> values, String throughValue) {
        this.name = name;
        this.values = values;
        this.throughValue = throughValue;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }

    public String getThroughValue() {
        return throughValue;
    }

    public void setThroughValue(String throughValue) {
        this.throughValue = throughValue;
    }
}
