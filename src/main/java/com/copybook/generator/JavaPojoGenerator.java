package com.copybook.generator;

import com.copybook.parser.model.CobolCondition;
import com.copybook.parser.model.CobolCopybook;
import com.copybook.parser.model.CobolDataItem;
import com.copybook.parser.model.CobolUsage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates Java POJO source files from a parsed {@link CobolCopybook}.
 * <p>
 * Produces one class per 01-level record, child classes for nested groups,
 * a wrapper class when multiple 01-level records exist, enum types for
 * level-88 conditions, and reuses shared structures detected across records.
 */
public class JavaPojoGenerator {

    private final String packageName;
    private final SharedStructureDetector structureDetector;
    private Map<String, SharedStructureDetector.SharedStructure> sharedStructures;

    /** Tracks which shared structures have already been emitted as files. */
    private final Set<String> emittedSharedClasses = new LinkedHashSet<>();

    /** Collects all generated files: className -> source code. */
    private final Map<String, String> generatedFiles = new LinkedHashMap<>();

    public JavaPojoGenerator(String packageName) {
        this.packageName = packageName;
        this.structureDetector = new SharedStructureDetector();
    }

    /**
     * Generates Java source files for the given copybook and writes them
     * to the output directory.
     *
     * @return map of file name -> generated source code
     */
    public Map<String, String> generate(CobolCopybook copybook) {
        generatedFiles.clear();
        emittedSharedClasses.clear();

        // Detect shared structures
        sharedStructures = structureDetector.detect(copybook);

        // Generate shared structure classes first
        for (var entry : sharedStructures.entrySet()) {
            var shared = entry.getValue();
            if (!emittedSharedClasses.contains(shared.canonicalClassName())) {
                String source = generateGroupClass(shared.canonicalDefinition(), shared.canonicalClassName());
                generatedFiles.put(shared.canonicalClassName() + ".java", source);
                emittedSharedClasses.add(shared.canonicalClassName());
            }
        }

        // Generate one class per 01-level record
        for (CobolDataItem record : copybook.getRecords()) {
            String className = NamingConverter.toPascalCase(record.getName());
            String source = generateRecordClass(record, className);
            generatedFiles.put(className + ".java", source);
        }

        // Generate wrapper if multiple 01-level records
        if (copybook.hasMultipleRecords()) {
            String wrapperName = NamingConverter.toPascalCase(copybook.getSourceName()) + "Root";
            String source = generateWrapperClass(copybook, wrapperName);
            generatedFiles.put(wrapperName + ".java", source);
        }

        // Generate standalone items (level 77) if any
        if (!copybook.getStandaloneItems().isEmpty()) {
            String holderName = NamingConverter.toPascalCase(copybook.getSourceName()) + "Standalone";
            String source = generateStandaloneHolder(copybook.getStandaloneItems(), holderName);
            generatedFiles.put(holderName + ".java", source);
        }

        return generatedFiles;
    }

    /**
     * Writes all generated files to disk.
     */
    public void writeTo(Path outputDir) throws IOException {
        Path packageDir = outputDir;
        if (packageName != null && !packageName.isBlank()) {
            packageDir = outputDir.resolve(packageName.replace('.', '/'));
        }
        Files.createDirectories(packageDir);

        for (var entry : generatedFiles.entrySet()) {
            Path filePath = packageDir.resolve(entry.getKey());
            Files.writeString(filePath, entry.getValue());
        }
    }

    // -----------------------------------------------------------------------
    // Record class generation
    // -----------------------------------------------------------------------

    private String generateRecordClass(CobolDataItem record, String className) {
        Set<String> imports = new LinkedHashSet<>();
        StringBuilder fields = new StringBuilder();
        StringBuilder innerClasses = new StringBuilder();
        StringBuilder enums = new StringBuilder();

        // Detect date pairs among children
        List<DateDetector.DatePair> datePairs = DateDetector.detectDates(record.getChildren());
        Set<CobolDataItem> dateConsumed = new LinkedHashSet<>();
        for (DateDetector.DatePair dp : datePairs) {
            dateConsumed.add(dp.relField());
            dateConsumed.add(dp.relDField());
        }

        // Process children
        for (CobolDataItem child : record.getChildren()) {
            if (child.isFiller()) continue;
            if (child.isLevel66()) {
                fields.append(generateLevel66Comment(child));
                continue;
            }
            if (dateConsumed.contains(child)) continue;

            generateField(child, record, className, fields, innerClasses, enums, imports);
        }

        // Generate date fields
        for (DateDetector.DatePair dp : datePairs) {
            generateDateField(dp, fields, imports);
        }

        return buildClassSource(className, imports, fields, innerClasses, enums);
    }

    private String generateGroupClass(CobolDataItem group, String className) {
        Set<String> imports = new LinkedHashSet<>();
        StringBuilder fields = new StringBuilder();
        StringBuilder innerClasses = new StringBuilder();
        StringBuilder enums = new StringBuilder();

        List<DateDetector.DatePair> datePairs = DateDetector.detectDates(group.getChildren());
        Set<CobolDataItem> dateConsumed = new LinkedHashSet<>();
        for (DateDetector.DatePair dp : datePairs) {
            dateConsumed.add(dp.relField());
            dateConsumed.add(dp.relDField());
        }

        for (CobolDataItem child : group.getChildren()) {
            if (child.isFiller()) continue;
            if (child.isLevel66()) {
                fields.append(generateLevel66Comment(child));
                continue;
            }
            if (dateConsumed.contains(child)) continue;

            generateField(child, group, className, fields, innerClasses, enums, imports);
        }

        for (DateDetector.DatePair dp : datePairs) {
            generateDateField(dp, fields, imports);
        }

        return buildClassSource(className, imports, fields, innerClasses, enums);
    }

    // -----------------------------------------------------------------------
    // Field generation
    // -----------------------------------------------------------------------

    private void generateField(CobolDataItem item, CobolDataItem parent, String parentClassName,
                               StringBuilder fields, StringBuilder innerClasses,
                               StringBuilder enums, Set<String> imports) {
        String fieldName = NamingConverter.toCamelCase(item.getName());

        if (item.hasRedefines()) {
            generateRedefinesField(item, parent, parentClassName, fields, innerClasses, enums, imports);
            return;
        }

        if (item.isGroup()) {
            generateGroupField(item, parentClassName, fieldName, fields, innerClasses, enums, imports);
            return;
        }

        // Elementary field
        String javaType = TypeMapper.mapType(item);
        addImport(imports, javaType);

        // Javadoc with COBOL metadata
        fields.append(generateFieldJavadoc(item));

        // @CobolField annotation
        fields.append(generateCobolFieldAnnotation(item));

        // Fixed-length annotation for PIC X
        if (item.isAlphanumeric() && item.getAlphanumericLength() > 0) {
            fields.append("    @CobolFixedLength(length = ").append(item.getAlphanumericLength())
                    .append(", padChar = ' ', trimOnRead = true)\n");
            fields.append("    @Size(max = ").append(item.getAlphanumericLength()).append(")\n");
            addImport(imports, "jakarta.validation.constraints.Size");
        }

        // @Digits for decimal fields
        if (item.hasDecimal()) {
            fields.append("    @Digits(integer = ").append(item.getIntegerDigits())
                    .append(", fraction = ").append(item.getDecimalDigits()).append(")\n");
            addImport(imports, "jakarta.validation.constraints.Digits");
        }

        // OCCURS -> List<Type>
        if (item.hasOccurs()) {
            generateOccursAnnotation(item, fields);
            fields.append("    private List<").append(javaType).append("> ").append(fieldName).append(";\n\n");
            imports.add("java.util.List");
        } else {
            fields.append("    private ").append(javaType).append(" ").append(fieldName).append(";\n\n");
        }

        // Level 88 conditions -> enum
        if (!item.getConditions().isEmpty()) {
            generateConditionEnum(item, fields, enums, imports);
        }
    }

    private void generateGroupField(CobolDataItem item, String parentClassName, String fieldName,
                                    StringBuilder fields, StringBuilder innerClasses,
                                    StringBuilder enums, Set<String> imports) {
        // Check if this is a shared structure
        String sharedClassName = structureDetector.getSharedClassName(item, sharedStructures);
        String childClassName;

        if (sharedClassName != null && emittedSharedClasses.contains(sharedClassName)) {
            childClassName = sharedClassName;
        } else {
            childClassName = NamingConverter.toPascalCase(item.getName());
            // Generate as inner class only if not shared
            if (sharedClassName == null) {
                String innerSource = generateInnerGroupClass(item, childClassName, imports);
                innerClasses.append(innerSource);
            }
        }

        fields.append(generateFieldJavadoc(item));

        if (item.hasOccurs()) {
            generateOccursAnnotation(item, fields);
            fields.append("    private List<").append(childClassName).append("> ")
                    .append(fieldName).append(";\n\n");
            imports.add("java.util.List");
        } else {
            fields.append("    private ").append(childClassName).append(" ")
                    .append(fieldName).append(";\n\n");
        }
    }

    private void generateRedefinesField(CobolDataItem item, CobolDataItem parent, String parentClassName,
                                        StringBuilder fields, StringBuilder innerClasses,
                                        StringBuilder enums, Set<String> imports) {
        String fieldName = NamingConverter.toCamelCase(item.getName());

        fields.append(generateFieldJavadoc(item));
        fields.append("    @CobolRedefines(target = \"").append(item.getRedefines()).append("\")\n");

        if (item.isGroup()) {
            // Union-style wrapper: generate a wrapper class
            String wrapperClassName = NamingConverter.toPascalCase(item.getName());
            String innerSource = generateInnerGroupClass(item, wrapperClassName, imports);
            innerClasses.append(innerSource);
            fields.append("    private ").append(wrapperClassName).append(" ").append(fieldName).append(";\n\n");
        } else {
            String javaType = TypeMapper.mapType(item);
            addImport(imports, javaType);
            fields.append(generateCobolFieldAnnotation(item));
            fields.append("    private ").append(javaType).append(" ").append(fieldName).append(";\n\n");
        }
    }

    private void generateDateField(DateDetector.DatePair dp, StringBuilder fields, Set<String> imports) {
        String typeName = dp.dateType() == DateDetector.DateType.LOCAL_DATE ? "LocalDate" : "YearMonth";
        addImport(imports, typeName);

        fields.append("    /**\n");
        fields.append("     * Inferred date from adjacent fields:\n");
        fields.append("     * REL field: ").append(dp.relField().getName())
                .append(" (PIC ").append(dp.relField().getPic()).append(")\n");
        fields.append("     * REL-D field: ").append(dp.relDField().getName())
                .append(" (PIC ").append(dp.relDField().getPic()).append(")\n");
        fields.append("     */\n");
        fields.append("    @CobolDateFormat(type = CobolDateType.")
                .append(dp.dateType().name()).append(")\n");
        fields.append("    private ").append(typeName).append(" ").append(dp.javaFieldName()).append(";\n\n");
    }

    private String generateInnerGroupClass(CobolDataItem group, String className, Set<String> parentImports) {
        StringBuilder sb = new StringBuilder();
        Set<String> innerImports = new LinkedHashSet<>();
        StringBuilder innerFields = new StringBuilder();
        StringBuilder nestedInner = new StringBuilder();
        StringBuilder nestedEnums = new StringBuilder();

        List<DateDetector.DatePair> datePairs = DateDetector.detectDates(group.getChildren());
        Set<CobolDataItem> dateConsumed = new LinkedHashSet<>();
        for (DateDetector.DatePair dp : datePairs) {
            dateConsumed.add(dp.relField());
            dateConsumed.add(dp.relDField());
        }

        for (CobolDataItem child : group.getChildren()) {
            if (child.isFiller()) continue;
            if (child.isLevel66()) {
                innerFields.append(generateLevel66Comment(child));
                continue;
            }
            if (dateConsumed.contains(child)) continue;

            generateField(child, group, className, innerFields, nestedInner, nestedEnums, innerImports);
        }

        for (DateDetector.DatePair dp : datePairs) {
            generateDateField(dp, innerFields, innerImports);
        }

        // Merge inner imports into parent
        parentImports.addAll(innerImports);

        sb.append("\n    @Data\n");
        sb.append("    @NoArgsConstructor\n");
        sb.append("    @AllArgsConstructor\n");
        sb.append("    @Builder\n");
        sb.append("    public static class ").append(className).append(" {\n\n");
        sb.append(indent(innerFields.toString(), 2));
        if (!nestedInner.isEmpty()) {
            sb.append(indent(nestedInner.toString(), 2));
        }
        if (!nestedEnums.isEmpty()) {
            sb.append(indent(nestedEnums.toString(), 2));
        }
        sb.append("    }\n");

        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Condition / enum generation
    // -----------------------------------------------------------------------

    private void generateConditionEnum(CobolDataItem item, StringBuilder fields,
                                       StringBuilder enums, Set<String> imports) {
        String enumTypeName = NamingConverter.toEnumTypeName(item.getName());
        String enumFieldName = NamingConverter.toCamelCase(item.getName()) + "Enum";

        // Condition names annotation on the raw field
        StringBuilder condAnn = new StringBuilder();
        condAnn.append("    @CobolConditionNames({\n");
        for (int i = 0; i < item.getConditions().size(); i++) {
            CobolCondition c = item.getConditions().get(i);
            condAnn.append("        @CobolConditionName(name = \"").append(c.getName())
                    .append("\", value = \"").append(c.getValues().isEmpty() ? "" : c.getValues().get(0))
                    .append("\")");
            if (i < item.getConditions().size() - 1) condAnn.append(",");
            condAnn.append("\n");
        }
        condAnn.append("    })\n");

        // Insert the annotation before the field (already generated, so we append enum field)
        // The @CobolConditionNames was already handled in field generation via the annotation block
        // We just add the enum field reference
        fields.append(condAnn);
        fields.append("    private ").append(enumTypeName).append(" ").append(enumFieldName).append(";\n\n");

        // Generate the enum class
        enums.append("\n    public enum ").append(enumTypeName).append(" {\n");
        for (int i = 0; i < item.getConditions().size(); i++) {
            CobolCondition c = item.getConditions().get(i);
            String constName = NamingConverter.toEnumConstant(c.getName(), item.getName());
            String value = c.getValues().isEmpty() ? "" : c.getValues().get(0);

            enums.append("        ").append(constName).append("(\"").append(value).append("\")");
            if (i < item.getConditions().size() - 1) {
                enums.append(",\n");
            } else {
                enums.append(";\n");
            }
        }
        enums.append("\n        private final String value;\n\n");
        enums.append("        ").append(enumTypeName).append("(String value) {\n");
        enums.append("            this.value = value;\n");
        enums.append("        }\n\n");
        enums.append("        public String getValue() {\n");
        enums.append("            return value;\n");
        enums.append("        }\n");
        enums.append("    }\n");
    }

    // -----------------------------------------------------------------------
    // Annotation / comment generation
    // -----------------------------------------------------------------------

    private String generateFieldJavadoc(CobolDataItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("    /**\n");
        sb.append("     * COBOL Name: ").append(item.getName()).append("\n");
        sb.append("     * Level: ").append(String.format("%02d", item.getLevel())).append("\n");
        if (item.getPic() != null && !item.getPic().isBlank()) {
            sb.append("     * PIC: ").append(item.getPic()).append("\n");
        }
        sb.append("     * Usage: ").append(item.getUsage().getCobolName()).append("\n");
        if (item.isNumeric()) {
            sb.append("     * Signed: ").append(item.isSigned()).append("\n");
        }
        if (item.getOffset() >= 0) {
            sb.append("     * Offset: ").append(item.getOffset()).append("\n");
        }
        if (item.getLength() >= 0) {
            sb.append("     * Length: ").append(item.getLength()).append("\n");
        }
        if (item.hasRedefines()) {
            sb.append("     * Redefines: ").append(item.getRedefines()).append("\n");
        }
        sb.append("     */\n");
        return sb.toString();
    }

    private String generateCobolFieldAnnotation(CobolDataItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("    @CobolField(name = \"").append(item.getName()).append("\"");
        sb.append(", level = ").append(item.getLevel());
        if (item.getPic() != null && !item.getPic().isBlank()) {
            sb.append(", pic = \"").append(item.getPic()).append("\"");
        }
        sb.append(", usage = \"").append(item.getUsage().getCobolName()).append("\"");
        if (item.isSigned()) {
            sb.append(", signed = true");
        }
        if (item.getOffset() >= 0) {
            sb.append(", offset = ").append(item.getOffset());
        }
        if (item.getLength() >= 0) {
            sb.append(", length = ").append(item.getLength());
        }
        sb.append(")\n");
        return sb.toString();
    }

    private void generateOccursAnnotation(CobolDataItem item, StringBuilder fields) {
        fields.append("    @CobolOccurs(min = ").append(item.getOccursMin());
        fields.append(", max = ").append(item.getOccursMax());
        if (item.getDependingOn() != null && !item.getDependingOn().isBlank()) {
            fields.append(", dependingOn = \"").append(item.getDependingOn()).append("\"");
        }
        if (!item.getIndexedBy().isEmpty()) {
            fields.append(", indexedBy = {");
            for (int i = 0; i < item.getIndexedBy().size(); i++) {
                if (i > 0) fields.append(", ");
                fields.append("\"").append(item.getIndexedBy().get(i)).append("\"");
            }
            fields.append("}");
        }
        fields.append(")\n");
    }

    private String generateLevel66Comment(CobolDataItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("    /**\n");
        sb.append("     * COBOL Level 66 RENAMES: ").append(item.getName()).append("\n");
        sb.append("     * Renames: ").append(item.getRenamesFrom());
        if (item.getRenamesThrough() != null) {
            sb.append(" THROUGH ").append(item.getRenamesThrough());
        }
        sb.append("\n");
        sb.append("     * (Preserved as metadata; no direct Java field generated.)\n");
        sb.append("     */\n\n");
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Wrapper / standalone generation
    // -----------------------------------------------------------------------

    private String generateWrapperClass(CobolCopybook copybook, String className) {
        Set<String> imports = new LinkedHashSet<>();
        StringBuilder fields = new StringBuilder();

        for (CobolDataItem record : copybook.getRecords()) {
            String recClass = NamingConverter.toPascalCase(record.getName());
            String recField = NamingConverter.toCamelCase(record.getName());
            fields.append("    private ").append(recClass).append(" ").append(recField).append(";\n\n");
        }

        return buildClassSource(className, imports, fields, new StringBuilder(), new StringBuilder());
    }

    private String generateStandaloneHolder(List<CobolDataItem> items, String className) {
        Set<String> imports = new LinkedHashSet<>();
        StringBuilder fields = new StringBuilder();

        for (CobolDataItem item : items) {
            String javaType = TypeMapper.mapType(item);
            String fieldName = NamingConverter.toCamelCase(item.getName());
            addImport(imports, javaType);

            fields.append(generateFieldJavadoc(item));
            fields.append(generateCobolFieldAnnotation(item));
            fields.append("    private ").append(javaType).append(" ").append(fieldName).append(";\n\n");
        }

        return buildClassSource(className, imports, fields, new StringBuilder(), new StringBuilder());
    }

    // -----------------------------------------------------------------------
    // Source assembly
    // -----------------------------------------------------------------------

    private String buildClassSource(String className, Set<String> imports,
                                    StringBuilder fields, StringBuilder innerClasses,
                                    StringBuilder enums) {
        StringBuilder sb = new StringBuilder();

        // Package
        if (packageName != null && !packageName.isBlank()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }

        // Imports
        Set<String> allImports = new LinkedHashSet<>();

        // Always add Lombok
        allImports.add("lombok.AllArgsConstructor");
        allImports.add("lombok.Builder");
        allImports.add("lombok.Data");
        allImports.add("lombok.NoArgsConstructor");

        // Add annotation imports from our package
        String annotationPkg = (packageName != null && !packageName.isBlank())
                ? packageName + ".annotation" : "annotation";
        // We use star import for our annotations
        allImports.add(annotationPkg + ".*");

        // Add type-specific imports
        for (String imp : imports) {
            if (imp != null && !imp.startsWith("java.lang")) {
                allImports.add(imp);
            }
        }

        // Check field/inner content for types that need imports
        String content = fields.toString() + innerClasses.toString();
        if (content.contains("BigDecimal")) allImports.add("java.math.BigDecimal");
        if (content.contains("LocalDate")) allImports.add("java.time.LocalDate");
        if (content.contains("YearMonth")) allImports.add("java.time.YearMonth");
        if (content.contains("List<")) allImports.add("java.util.List");
        if (content.contains("@Size")) allImports.add("jakarta.validation.constraints.Size");
        if (content.contains("@Digits")) allImports.add("jakarta.validation.constraints.Digits");

        // Sort and write imports
        List<String> sortedImports = new ArrayList<>(allImports);
        sortedImports.sort(String::compareTo);

        for (String imp : sortedImports) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append("\n");

        // Class declaration
        sb.append("@Data\n");
        sb.append("@NoArgsConstructor\n");
        sb.append("@AllArgsConstructor\n");
        sb.append("@Builder\n");
        sb.append("public class ").append(className).append(" {\n\n");

        // Fields
        sb.append(fields);

        // Inner classes
        if (!innerClasses.isEmpty()) {
            sb.append(innerClasses);
        }

        // Enums
        if (!enums.isEmpty()) {
            sb.append(enums);
        }

        sb.append("}\n");

        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private void addImport(Set<String> imports, String typeName) {
        String imp = TypeMapper.getImport(typeName);
        if (imp != null) {
            imports.add(imp);
        }
    }

    private String indent(String text, int extraLevels) {
        String indent = "    ".repeat(extraLevels);
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            if (line.isBlank()) {
                sb.append("\n");
            } else {
                sb.append(indent).append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
