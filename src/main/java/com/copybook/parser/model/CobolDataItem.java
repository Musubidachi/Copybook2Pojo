package com.copybook.parser.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single COBOL data item (a line/entry in the copybook).
 * Models levels 01-49, 66, 77, and 88.
 */
public class CobolDataItem {

    private int level;
    private String name;
    private String pic;
    private CobolUsage usage = CobolUsage.DISPLAY;
    private boolean signed;

    // OCCURS
    private int occursMin;
    private int occursMax;
    private String dependingOn;
    private List<String> indexedBy = new ArrayList<>();

    // REDEFINES
    private String redefines;

    // Level 66 RENAMES
    private String renamesFrom;
    private String renamesThrough;

    // Level 88 conditions associated with this item
    private List<CobolCondition> conditions = new ArrayList<>();

    // Structural
    private List<CobolDataItem> children = new ArrayList<>();
    private CobolDataItem parent;
    private boolean filler;

    // Computed layout
    private int offset = -1;
    private int length = -1;

    // Decimal info parsed from PIC
    private int integerDigits;
    private int decimalDigits;
    private int alphanumericLength;

    // Raw source line for diagnostics
    private String rawLine;

    public CobolDataItem() {
    }

    // --- Convenience queries ---

    public boolean isGroup() {
        return !children.isEmpty() && (pic == null || pic.isBlank());
    }

    public boolean isElementary() {
        return !isGroup();
    }

    public boolean isLevel88() {
        return level == 88;
    }

    public boolean isLevel66() {
        return level == 66;
    }

    public boolean isLevel77() {
        return level == 77;
    }

    public boolean isLevel01() {
        return level == 1;
    }

    public boolean hasOccurs() {
        return occursMax > 0;
    }

    public boolean hasRedefines() {
        return redefines != null && !redefines.isBlank();
    }

    public boolean hasDecimal() {
        return decimalDigits > 0;
    }

    public boolean isNumeric() {
        return pic != null && pic.toUpperCase().contains("9");
    }

    public boolean isAlphanumeric() {
        return pic != null && pic.toUpperCase().contains("X");
    }

    public void addChild(CobolDataItem child) {
        child.setParent(this);
        this.children.add(child);
    }

    public void addCondition(CobolCondition condition) {
        this.conditions.add(condition);
    }

    // --- Getters and setters ---

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPic() {
        return pic;
    }

    public void setPic(String pic) {
        this.pic = pic;
    }

    public CobolUsage getUsage() {
        return usage;
    }

    public void setUsage(CobolUsage usage) {
        this.usage = usage;
    }

    public boolean isSigned() {
        return signed;
    }

    public void setSigned(boolean signed) {
        this.signed = signed;
    }

    public int getOccursMin() {
        return occursMin;
    }

    public void setOccursMin(int occursMin) {
        this.occursMin = occursMin;
    }

    public int getOccursMax() {
        return occursMax;
    }

    public void setOccursMax(int occursMax) {
        this.occursMax = occursMax;
    }

    public String getDependingOn() {
        return dependingOn;
    }

    public void setDependingOn(String dependingOn) {
        this.dependingOn = dependingOn;
    }

    public List<String> getIndexedBy() {
        return indexedBy;
    }

    public void setIndexedBy(List<String> indexedBy) {
        this.indexedBy = indexedBy;
    }

    public String getRedefines() {
        return redefines;
    }

    public void setRedefines(String redefines) {
        this.redefines = redefines;
    }

    public String getRenamesFrom() {
        return renamesFrom;
    }

    public void setRenamesFrom(String renamesFrom) {
        this.renamesFrom = renamesFrom;
    }

    public String getRenamesThrough() {
        return renamesThrough;
    }

    public void setRenamesThrough(String renamesThrough) {
        this.renamesThrough = renamesThrough;
    }

    public List<CobolCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<CobolCondition> conditions) {
        this.conditions = conditions;
    }

    public List<CobolDataItem> getChildren() {
        return children;
    }

    public void setChildren(List<CobolDataItem> children) {
        this.children = children;
    }

    public CobolDataItem getParent() {
        return parent;
    }

    public void setParent(CobolDataItem parent) {
        this.parent = parent;
    }

    public boolean isFiller() {
        return filler;
    }

    public void setFiller(boolean filler) {
        this.filler = filler;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public int getIntegerDigits() {
        return integerDigits;
    }

    public void setIntegerDigits(int integerDigits) {
        this.integerDigits = integerDigits;
    }

    public int getDecimalDigits() {
        return decimalDigits;
    }

    public void setDecimalDigits(int decimalDigits) {
        this.decimalDigits = decimalDigits;
    }

    public int getAlphanumericLength() {
        return alphanumericLength;
    }

    public void setAlphanumericLength(int alphanumericLength) {
        this.alphanumericLength = alphanumericLength;
    }

    public String getRawLine() {
        return rawLine;
    }

    public void setRawLine(String rawLine) {
        this.rawLine = rawLine;
    }

    @Override
    public String toString() {
        return "CobolDataItem{level=" + level + ", name='" + name + "', pic='" + pic + "'}";
    }
}
