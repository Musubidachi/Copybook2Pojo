package com.copybook.generator;

import com.copybook.parser.model.CobolDataItem;
import com.copybook.parser.model.CobolUsage;

import java.util.List;

/**
 * Detects date patterns in adjacent COBOL fields.
 * Specifically looks for REL/REL-D naming patterns and
 * other date-indicating field adjacency.
 */
public final class DateDetector {

    private DateDetector() {
    }

    /**
     * Represents a detected date field pair.
     */
    public record DatePair(
            CobolDataItem relField,
            CobolDataItem relDField,
            DateType dateType,
            String javaFieldName
    ) {
    }

    public enum DateType {
        LOCAL_DATE,
        YEAR_MONTH
    }

    /**
     * Scans a list of sibling fields for date patterns.
     * Returns a list of detected date pairs. The original fields
     * should be replaced by a single date-typed field.
     */
    public static List<DatePair> detectDates(List<CobolDataItem> siblings) {
        java.util.List<DatePair> pairs = new java.util.ArrayList<>();

        for (int i = 0; i < siblings.size() - 1; i++) {
            CobolDataItem current = siblings.get(i);
            CobolDataItem next = siblings.get(i + 1);

            if (isRelPair(current, next)) {
                DateType type = inferDateType(current, next);
                String baseName = extractDateBaseName(current.getName());
                String javaName = NamingConverter.toCamelCase(baseName + "-DATE");

                pairs.add(new DatePair(current, next, type, javaName));
                i++; // skip the next field since it's part of the pair
            }
        }

        return pairs;
    }

    /**
     * Checks if two adjacent fields match the REL/REL-D pattern.
     * The first field should end with -REL and the second with -REL-D.
     */
    private static boolean isRelPair(CobolDataItem first, CobolDataItem second) {
        if (first.getName() == null || second.getName() == null) return false;

        String firstName = first.getName().toUpperCase();
        String secondName = second.getName().toUpperCase();

        // Pattern: X-REL followed by X-REL-D
        if (firstName.endsWith("-REL") && secondName.endsWith("-REL-D")) {
            String base1 = firstName.substring(0, firstName.length() - 4);
            String base2 = secondName.substring(0, secondName.length() - 6);
            return base1.equals(base2);
        }

        return false;
    }

    /**
     * Infers whether a REL/REL-D pair represents a LocalDate or YearMonth.
     * Uses the total precision of the pair to decide.
     */
    private static DateType inferDateType(CobolDataItem relField, CobolDataItem relDField) {
        int relDigits = relField.getIntegerDigits();
        int relDDigits = relDField.getIntegerDigits();

        // REL typically holds a date offset; REL-D a day component
        // If REL-D has 2 digits (day precision) -> LocalDate
        // If REL-D has fewer digits or unusual layout -> YearMonth
        if (relDDigits >= 2) {
            return DateType.LOCAL_DATE;
        }

        // Check total precision: 6 digits (YYMMDD) or 8 digits (YYYYMMDD) -> LocalDate
        // 4 digits (YYMM) or 6 without day -> YearMonth
        int totalDigits = relDigits + relDDigits;
        if (totalDigits >= 6) {
            return DateType.LOCAL_DATE;
        }

        return DateType.YEAR_MONTH;
    }

    /**
     * Extracts the base name from a REL field name.
     * E.g., "RELEASE-REL" -> "RELEASE"
     */
    private static String extractDateBaseName(String relFieldName) {
        String upper = relFieldName.toUpperCase();
        if (upper.endsWith("-REL")) {
            return upper.substring(0, upper.length() - 4);
        }
        return upper;
    }
}
