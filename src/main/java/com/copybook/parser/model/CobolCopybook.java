package com.copybook.parser.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a parsed COBOL copybook containing one or more 01-level records
 * and any standalone 77-level items.
 */
public class CobolCopybook {

    private String sourceName;
    private List<CobolDataItem> records = new ArrayList<>();
    private List<CobolDataItem> standaloneItems = new ArrayList<>();

    public CobolCopybook() {
    }

    public CobolCopybook(String sourceName) {
        this.sourceName = sourceName;
    }

    /**
     * Adds a top-level 01 record.
     */
    public void addRecord(CobolDataItem record) {
        records.add(record);
    }

    /**
     * Adds a standalone item (level 77).
     */
    public void addStandaloneItem(CobolDataItem item) {
        standaloneItems.add(item);
    }

    /**
     * Returns true if this copybook contains multiple 01-level records,
     * requiring a wrapper/root POJO.
     */
    public boolean hasMultipleRecords() {
        return records.size() > 1;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public List<CobolDataItem> getRecords() {
        return records;
    }

    public void setRecords(List<CobolDataItem> records) {
        this.records = records;
    }

    public List<CobolDataItem> getStandaloneItems() {
        return standaloneItems;
    }

    public void setStandaloneItems(List<CobolDataItem> standaloneItems) {
        this.standaloneItems = standaloneItems;
    }
}
