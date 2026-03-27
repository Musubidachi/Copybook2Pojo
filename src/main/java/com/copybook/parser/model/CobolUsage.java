package com.copybook.parser.model;

/**
 * Enumerates COBOL USAGE types that affect how a field is stored
 * and how it maps to Java types.
 */
public enum CobolUsage {

    DISPLAY("DISPLAY"),
    COMP("COMP"),
    COMP_1("COMP-1"),
    COMP_2("COMP-2"),
    COMP_3("COMP-3"),
    COMP_4("COMP-4"),
    BINARY("BINARY"),
    PACKED_DECIMAL("PACKED-DECIMAL"),
    INDEX("INDEX");

    private final String cobolName;

    CobolUsage(String cobolName) {
        this.cobolName = cobolName;
    }

    public String getCobolName() {
        return cobolName;
    }

    /**
     * Parses a COBOL usage string into the enum value.
     */
    public static CobolUsage fromString(String s) {
        if (s == null || s.isBlank()) {
            return DISPLAY;
        }
        String upper = s.trim().toUpperCase().replace("USAGE ", "").replace("IS ", "").trim();
        return switch (upper) {
            case "COMP", "COMPUTATIONAL" -> COMP;
            case "COMP-1", "COMPUTATIONAL-1" -> COMP_1;
            case "COMP-2", "COMPUTATIONAL-2" -> COMP_2;
            case "COMP-3", "COMPUTATIONAL-3", "PACKED-DECIMAL" -> COMP_3;
            case "COMP-4", "COMPUTATIONAL-4" -> COMP_4;
            case "BINARY" -> BINARY;
            case "INDEX" -> INDEX;
            default -> DISPLAY;
        };
    }
}
