package com.copybook.generator;

import com.copybook.parser.model.CobolDataItem;
import com.copybook.parser.model.CobolUsage;

/**
 * Maps COBOL PIC/USAGE combinations to Java type names,
 * following the rules from the conversion guide.
 */
public final class TypeMapper {

    private TypeMapper() {
    }

    /**
     * Determines the Java type string for a given COBOL data item.
     *
     * @param item the parsed COBOL data item
     * @return the simple Java type name (e.g. "String", "BigDecimal", "Integer")
     */
    public static String mapType(CobolDataItem item) {
        // COMP-1 -> Float
        if (item.getUsage() == CobolUsage.COMP_1) {
            return "Float";
        }

        // COMP-2 -> Double
        if (item.getUsage() == CobolUsage.COMP_2) {
            return "Double";
        }

        // Alphanumeric (PIC X)
        if (item.isAlphanumeric()) {
            return "String";
        }

        // Numeric fields
        if (item.isNumeric()) {
            // Any implied decimal -> BigDecimal
            if (item.hasDecimal()) {
                return "BigDecimal";
            }

            // COMP-3 (packed decimal) -> BigDecimal for safety
            if (item.getUsage() == CobolUsage.COMP_3 || item.getUsage() == CobolUsage.PACKED_DECIMAL) {
                return "BigDecimal";
            }

            // Integer numerics: choose by total digit count
            int totalDigits = item.getIntegerDigits();
            return mapIntegerType(totalDigits, item.getUsage());
        }

        // Fallback: if no PIC but has USAGE (e.g. COMP-1/COMP-2 without PIC)
        if (item.getPic() == null || item.getPic().isBlank()) {
            return switch (item.getUsage()) {
                case COMP_1 -> "Float";
                case COMP_2 -> "Double";
                case INDEX -> "Integer";
                default -> "String";
            };
        }

        return "String";
    }

    /**
     * Maps integer numeric COBOL fields to Java wrapper types based on digit count.
     */
    private static String mapIntegerType(int totalDigits, CobolUsage usage) {
        if (usage == CobolUsage.COMP || usage == CobolUsage.COMP_4 || usage == CobolUsage.BINARY) {
            // Binary/COMP: storage-size based
            if (totalDigits <= 4) return "Short";
            if (totalDigits <= 9) return "Integer";
            if (totalDigits <= 18) return "Long";
            return "BigDecimal";
        }

        // DISPLAY or other: digit-count based
        if (totalDigits <= 9) return "Integer";
        if (totalDigits <= 18) return "Long";
        return "BigDecimal";
    }

    /**
     * Returns the fully qualified import for a type, or null if it's a java.lang type.
     */
    public static String getImport(String typeName) {
        return switch (typeName) {
            case "BigDecimal" -> "java.math.BigDecimal";
            case "LocalDate" -> "java.time.LocalDate";
            case "YearMonth" -> "java.time.YearMonth";
            case "List" -> "java.util.List";
            default -> null; // java.lang types don't need import
        };
    }

    /**
     * Returns true if this type requires a List wrapper (i.e. the item has OCCURS).
     */
    public static boolean requiresList(CobolDataItem item) {
        return item.hasOccurs();
    }
}
