package com.copybook.generator;

/**
 * Converts COBOL names (UPPER-CASE-HYPHENATED) into
 * Java camelCase field names and PascalCase class names.
 */
public final class NamingConverter {

    private NamingConverter() {
    }

    /**
     * Converts a COBOL name to Java camelCase.
     * Example: "CUSTOMER-NUMBER" -> "customerNumber"
     */
    public static String toCamelCase(String cobolName) {
        if (cobolName == null || cobolName.isBlank()) return "unnamed";

        String[] parts = cobolName.split("[-_]+");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) continue;

            if (i == 0) {
                sb.append(part.toLowerCase());
            } else {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1).toLowerCase());
                }
            }
        }

        String result = sb.toString();
        // Avoid Java keywords
        return sanitizeJavaIdentifier(result);
    }

    /**
     * Converts a COBOL name to Java PascalCase (for class names).
     * Example: "CUSTOMER-REC" -> "CustomerRec"
     */
    public static String toPascalCase(String cobolName) {
        if (cobolName == null || cobolName.isBlank()) return "Unnamed";

        String[] parts = cobolName.split("[-_]+");
        StringBuilder sb = new StringBuilder();

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            sb.append(Character.toUpperCase(trimmed.charAt(0)));
            if (trimmed.length() > 1) {
                sb.append(trimmed.substring(1).toLowerCase());
            }
        }

        return sb.toString();
    }

    /**
     * Converts a COBOL condition name (level 88) to Java enum constant style.
     * Example: "STATUS-ACTIVE" -> "STATUS_ACTIVE"
     *
     * If the parent field name is a prefix, it is stripped:
     * parent="STATUS-CODE", condition="STATUS-ACTIVE" -> "ACTIVE"
     */
    public static String toEnumConstant(String conditionName, String parentName) {
        String upper = conditionName.toUpperCase().replace("-", "_");

        if (parentName != null && !parentName.isBlank()) {
            // First try exact parent name as prefix
            String prefix = parentName.toUpperCase().replace("-", "_") + "_";
            if (upper.startsWith(prefix) && upper.length() > prefix.length()) {
                return upper.substring(prefix.length());
            }

            // Strip leading words from the condition name that appear in the parent's word set
            String[] condParts = upper.split("_");
            java.util.Set<String> parentWords = new java.util.HashSet<>(
                    java.util.Arrays.asList(parentName.toUpperCase().replace("-", "_").split("_")));
            int strip = 0;
            while (strip < condParts.length - 1 && parentWords.contains(condParts[strip])) {
                strip++;
            }
            if (strip > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = strip; i < condParts.length; i++) {
                    if (!sb.isEmpty()) sb.append("_");
                    sb.append(condParts[i]);
                }
                return sb.toString();
            }
        }

        return upper;
    }

    /**
     * Converts a COBOL name to a Java enum type name (PascalCase).
     * Example: "CUSTOMER-STATUS" -> "CustomerStatus"
     */
    public static String toEnumTypeName(String cobolName) {
        return toPascalCase(cobolName);
    }

    /**
     * Sanitizes a Java identifier to avoid reserved keywords.
     */
    private static String sanitizeJavaIdentifier(String name) {
        return switch (name) {
            case "abstract", "assert", "boolean", "break", "byte", "case", "catch",
                 "char", "class", "const", "continue", "default", "do", "double",
                 "else", "enum", "extends", "final", "finally", "float", "for",
                 "goto", "if", "implements", "import", "instanceof", "int",
                 "interface", "long", "native", "new", "package", "private",
                 "protected", "public", "return", "short", "static", "strictfp",
                 "super", "switch", "synchronized", "this", "throw", "throws",
                 "transient", "try", "void", "volatile", "while" -> name + "Field";
            default -> name;
        };
    }
}
