package com.copybook.parser;

import com.copybook.parser.model.CobolCondition;
import com.copybook.parser.model.CobolCopybook;
import com.copybook.parser.model.CobolDataItem;
import com.copybook.parser.model.CobolUsage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses COBOL copybook source text into a {@link CobolCopybook} model.
 * <p>
 * Handles free-format and fixed-format (columns 7-72) copybooks.
 * Supports levels 01-49, 66, 77, 88, OCCURS, OCCURS DEPENDING ON,
 * REDEFINES, RENAMES, PIC, USAGE/COMP clauses, FILLER, and INDEXED BY.
 */
public class CopybookParser {

    // Pattern fragments
    private static final Pattern LEVEL_NAME_PATTERN =
            Pattern.compile("^\\s*(\\d{1,2})\\s+(\\S+)");

    private static final Pattern PIC_PATTERN =
            Pattern.compile("PIC(?:TURE)?\\s+IS\\s+(\\S+)|PIC(?:TURE)?\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern USAGE_PATTERN =
            Pattern.compile("(?:USAGE\\s+(?:IS\\s+)?)(COMP(?:UTATIONAL)?(?:-[1-4])?|BINARY|PACKED-DECIMAL|DISPLAY|INDEX)" +
                    "|\\b(COMP(?:UTATIONAL)?(?:-[1-4])?|BINARY|PACKED-DECIMAL)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern OCCURS_PATTERN =
            Pattern.compile("OCCURS\\s+(\\d+)\\s+(?:TO\\s+(\\d+)\\s+)?TIMES?", Pattern.CASE_INSENSITIVE);

    private static final Pattern DEPENDING_ON_PATTERN =
            Pattern.compile("DEPENDING\\s+ON\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern INDEXED_BY_PATTERN =
            Pattern.compile("INDEXED\\s+BY\\s+(\\S+(?:\\s+\\S+)*?)(?=\\s*\\.?\\s*$|\\s+OCCURS|\\s+PIC|\\s+USAGE|\\s+REDEFINES)", Pattern.CASE_INSENSITIVE);

    private static final Pattern REDEFINES_PATTERN =
            Pattern.compile("REDEFINES\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern VALUE_PATTERN =
            Pattern.compile("VALUE\\s+(?:IS\\s+)?(.+?)(?:\\.|$)", Pattern.CASE_INSENSITIVE);

    private static final Pattern RENAMES_PATTERN =
            Pattern.compile("RENAMES\\s+(\\S+)(?:\\s+(?:THRU|THROUGH)\\s+(\\S+))?", Pattern.CASE_INSENSITIVE);

    // PIC analysis patterns
    private static final Pattern PIC_NINE_PATTERN =
            Pattern.compile("S?9+(?:\\((\\d+)\\))?", Pattern.CASE_INSENSITIVE);

    private static final Pattern PIC_X_PATTERN =
            Pattern.compile("X+(?:\\((\\d+)\\))?", Pattern.CASE_INSENSITIVE);

    private static final Pattern PIC_DECIMAL_PATTERN =
            Pattern.compile("V(9+(?:\\((\\d+)\\))?)", Pattern.CASE_INSENSITIVE);

    /**
     * Parses a copybook from a file path.
     */
    public CobolCopybook parse(Path path) throws IOException {
        String content = Files.readString(path);
        String sourceName = path.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        return parse(content, sourceName);
    }

    /**
     * Parses copybook source text.
     */
    public CobolCopybook parse(String source, String sourceName) {
        CobolCopybook copybook = new CobolCopybook(sourceName);
        List<String> logicalLines = preprocessLines(source);

        Deque<CobolDataItem> stack = new ArrayDeque<>();
        CobolDataItem lastItem = null;

        for (String line : logicalLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Extract level and name
            Matcher levelMatcher = LEVEL_NAME_PATTERN.matcher(trimmed);
            if (!levelMatcher.find()) continue;

            int level = Integer.parseInt(levelMatcher.group(1));
            String name = levelMatcher.group(2).toUpperCase();

            // Handle level 88 (condition name)
            if (level == 88) {
                if (lastItem != null) {
                    CobolCondition condition = parseCondition(name, trimmed);
                    lastItem.addCondition(condition);
                }
                continue;
            }

            CobolDataItem item = new CobolDataItem();
            item.setLevel(level);
            item.setRawLine(trimmed);

            // Handle FILLER
            if ("FILLER".equalsIgnoreCase(name) || name.endsWith(".")) {
                String cleanName = name.replace(".", "");
                if ("FILLER".equalsIgnoreCase(cleanName)) {
                    item.setFiller(true);
                    item.setName("FILLER");
                } else {
                    item.setName(cleanName);
                }
            } else {
                item.setName(name.replace(".", ""));
            }

            // Parse PIC
            parsePic(item, trimmed);

            // Parse USAGE
            parseUsage(item, trimmed);

            // Parse OCCURS
            parseOccurs(item, trimmed);

            // Parse REDEFINES
            parseRedefines(item, trimmed);

            // Parse RENAMES (level 66)
            if (level == 66) {
                parseRenames(item, trimmed);
            }

            // Compute byte length for elementary items
            computeLength(item);

            // Build tree structure
            if (level == 1 || level == 77) {
                stack.clear();
                if (level == 77) {
                    copybook.addStandaloneItem(item);
                } else {
                    copybook.addRecord(item);
                }
                stack.push(item);
            } else if (level == 66) {
                // Level 66 attaches to the current 01 record
                if (!stack.isEmpty()) {
                    CobolDataItem root = stack.peekLast();
                    root.addChild(item);
                }
            } else {
                // Pop stack until we find an item whose level < current level
                while (!stack.isEmpty() && stack.peek().getLevel() >= level) {
                    stack.pop();
                }
                if (!stack.isEmpty()) {
                    stack.peek().addChild(item);
                }
                stack.push(item);
            }

            lastItem = item;
        }

        // Compute offsets
        for (CobolDataItem record : copybook.getRecords()) {
            computeOffsets(record, 0);
        }

        return copybook;
    }

    /**
     * Preprocesses raw copybook lines: strips sequence numbers (cols 1-6),
     * indicator (col 7), and content beyond col 72. Joins continuation lines.
     */
    private List<String> preprocessLines(String source) {
        String[] rawLines = source.split("\\r?\\n");
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String raw : rawLines) {
            String line;

            // If line looks like fixed-format (>= 7 chars and col 7 is indicator area)
            if (raw.length() >= 7 && raw.substring(0, 6).matches("\\d{6}")) {
                char indicator = raw.charAt(6);
                if (indicator == '*' || indicator == '/') continue; // comment
                line = raw.length() > 72 ? raw.substring(7, 72) : raw.substring(7);
                if (indicator == '-') {
                    // Continuation: append to current
                    current.append(line.stripLeading());
                    continue;
                }
            } else {
                // Free-format: skip comment lines starting with *
                line = raw;
                if (line.stripLeading().startsWith("*")) continue;
            }

            String stripped = line.stripTrailing();
            if (stripped.isBlank()) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            // If current is accumulating and this line starts a new statement
            // (i.e. starts with a level number), flush the previous statement first
            if (!current.isEmpty()) {
                String trimmedStripped = stripped.stripLeading();
                if (trimmedStripped.matches("^\\d{1,2}\\s+.*")) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            }

            // Accumulate multi-line statements (lines not ending with period)
            if (!current.isEmpty()) {
                current.append(" ");
            }
            current.append(stripped);
            if (stripped.endsWith(".")) {
                result.add(current.toString());
                current.setLength(0);
            }
        }

        if (!current.isEmpty()) {
            result.add(current.toString());
        }

        return result;
    }

    private CobolCondition parseCondition(String name, String line) {
        CobolCondition condition = new CobolCondition();
        condition.setName(name.replace(".", ""));

        Matcher valueMatcher = VALUE_PATTERN.matcher(line);
        List<String> values = new ArrayList<>();

        if (valueMatcher.find()) {
            String valueStr = valueMatcher.group(1).trim();
            // Handle THROUGH/THRU
            String throughValue = null;
            if (valueStr.toUpperCase().contains(" THRU ") || valueStr.toUpperCase().contains(" THROUGH ")) {
                String[] parts = valueStr.split("(?i)\\s+(?:THRU|THROUGH)\\s+");
                values.add(cleanValue(parts[0]));
                if (parts.length > 1) {
                    throughValue = cleanValue(parts[1]);
                }
            } else {
                // Handle multiple values separated by spaces or commas
                for (String v : valueStr.split("[,\\s]+")) {
                    String cleaned = cleanValue(v);
                    if (!cleaned.isEmpty()) {
                        values.add(cleaned);
                    }
                }
            }
            condition.setThroughValue(throughValue);
        }

        condition.setValues(values);
        return condition;
    }

    private String cleanValue(String v) {
        return v.trim().replace("'", "").replace("\"", "").replace(".", "");
    }

    private void parsePic(CobolDataItem item, String line) {
        Matcher picMatcher = PIC_PATTERN.matcher(line);
        if (!picMatcher.find()) return;

        String pic = picMatcher.group(1) != null ? picMatcher.group(1) : picMatcher.group(2);
        pic = pic.replace(".", "").trim();
        item.setPic(pic);

        // Check for sign
        if (pic.toUpperCase().startsWith("S")) {
            item.setSigned(true);
        }

        // Parse integer digits
        String upperPic = pic.toUpperCase();
        int intDigits = 0;
        int decDigits = 0;
        int alphaLen = 0;

        // Count integer 9s (before V)
        String beforeV = upperPic.contains("V") ? upperPic.substring(0, upperPic.indexOf('V')) : upperPic;
        intDigits = countDigits(beforeV, '9');

        // Count decimal 9s (after V)
        if (upperPic.contains("V")) {
            String afterV = upperPic.substring(upperPic.indexOf('V') + 1);
            decDigits = countDigits(afterV, '9');
        }

        // Count alphanumeric length
        alphaLen = countDigits(upperPic, 'X');
        if (alphaLen == 0 && upperPic.contains("A")) {
            alphaLen = countDigits(upperPic, 'A');
        }

        item.setIntegerDigits(intDigits);
        item.setDecimalDigits(decDigits);
        item.setAlphanumericLength(alphaLen);
    }

    /**
     * Counts the effective number of repetitions of a character in a PIC string,
     * handling the (n) repeat notation.
     */
    private int countDigits(String picPart, char target) {
        int count = 0;
        String upper = picPart.toUpperCase().replace("S", "");
        int i = 0;
        while (i < upper.length()) {
            char c = upper.charAt(i);
            if (c == Character.toUpperCase(target)) {
                // Check for (n) following
                if (i + 1 < upper.length() && upper.charAt(i + 1) == '(') {
                    int close = upper.indexOf(')', i + 2);
                    if (close > i + 2) {
                        count += Integer.parseInt(upper.substring(i + 2, close));
                        i = close + 1;
                        continue;
                    }
                }
                count++;
            }
            i++;
        }
        return count;
    }

    private void parseUsage(CobolDataItem item, String line) {
        Matcher usageMatcher = USAGE_PATTERN.matcher(line);
        if (usageMatcher.find()) {
            String usageStr = usageMatcher.group(1) != null ? usageMatcher.group(1) : usageMatcher.group(2);
            item.setUsage(CobolUsage.fromString(usageStr));
        }
    }

    private void parseOccurs(CobolDataItem item, String line) {
        Matcher occursMatcher = OCCURS_PATTERN.matcher(line);
        if (!occursMatcher.find()) return;

        int first = Integer.parseInt(occursMatcher.group(1));
        String second = occursMatcher.group(2);

        if (second != null) {
            // OCCURS n TO m TIMES
            item.setOccursMin(first);
            item.setOccursMax(Integer.parseInt(second));
        } else {
            // OCCURS n TIMES
            item.setOccursMin(first);
            item.setOccursMax(first);
        }

        // DEPENDING ON
        Matcher depMatcher = DEPENDING_ON_PATTERN.matcher(line);
        if (depMatcher.find()) {
            item.setDependingOn(depMatcher.group(1).replace(".", "").toUpperCase());
            // For ODO, min is typically 0
            if (second != null) {
                item.setOccursMin(first);
            } else {
                item.setOccursMin(0);
            }
        }

        // INDEXED BY
        Matcher idxMatcher = INDEXED_BY_PATTERN.matcher(line);
        if (idxMatcher.find()) {
            String idxStr = idxMatcher.group(1).trim();
            List<String> indexes = new ArrayList<>();
            for (String idx : idxStr.split("\\s+")) {
                String cleaned = idx.replace(".", "").trim();
                if (!cleaned.isEmpty()) {
                    indexes.add(cleaned.toUpperCase());
                }
            }
            item.setIndexedBy(indexes);
        }
    }

    private void parseRedefines(CobolDataItem item, String line) {
        Matcher redefMatcher = REDEFINES_PATTERN.matcher(line);
        if (redefMatcher.find()) {
            item.setRedefines(redefMatcher.group(1).replace(".", "").toUpperCase());
        }
    }

    private void parseRenames(CobolDataItem item, String line) {
        Matcher renamesMatcher = RENAMES_PATTERN.matcher(line);
        if (renamesMatcher.find()) {
            item.setRenamesFrom(renamesMatcher.group(1).replace(".", "").toUpperCase());
            if (renamesMatcher.group(2) != null) {
                item.setRenamesThrough(renamesMatcher.group(2).replace(".", "").toUpperCase());
            }
        }
    }

    /**
     * Computes the byte length for an elementary item based on PIC and USAGE.
     */
    private void computeLength(CobolDataItem item) {
        if (item.getPic() == null || item.getPic().isBlank()) return;

        int totalDigits = item.getIntegerDigits() + item.getDecimalDigits();
        int alphaLen = item.getAlphanumericLength();

        switch (item.getUsage()) {
            case DISPLAY -> {
                if (alphaLen > 0) {
                    item.setLength(alphaLen);
                } else {
                    // DISPLAY numeric: 1 byte per digit, +1 for sign if signed
                    item.setLength(totalDigits + (item.isSigned() ? 1 : 0));
                }
            }
            case COMP, COMP_4, BINARY -> {
                // Binary storage: 1-4 digits=2 bytes, 5-9=4 bytes, 10-18=8 bytes
                if (totalDigits <= 4) item.setLength(2);
                else if (totalDigits <= 9) item.setLength(4);
                else item.setLength(8);
            }
            case COMP_3, PACKED_DECIMAL -> {
                // Packed decimal: ceil((digits+1)/2)
                item.setLength((totalDigits + 2) / 2);
            }
            case COMP_1 -> item.setLength(4);
            case COMP_2 -> item.setLength(8);
            default -> {
                if (alphaLen > 0) item.setLength(alphaLen);
            }
        }
    }

    /**
     * Recursively computes byte offsets within a record.
     */
    private int computeOffsets(CobolDataItem item, int currentOffset) {
        item.setOffset(currentOffset);

        if (item.isGroup()) {
            int groupLength = 0;
            for (CobolDataItem child : item.getChildren()) {
                if (child.isLevel66() || child.isFiller()) {
                    // Fillers still contribute to offset for layout purposes
                    if (child.isFiller() && child.getLength() > 0) {
                        child.setOffset(currentOffset + groupLength);
                        int childLen = child.getLength();
                        if (child.hasOccurs()) childLen *= child.getOccursMax();
                        groupLength += childLen;
                    }
                    continue;
                }
                if (child.hasRedefines()) {
                    // REDEFINES items share the same offset as target
                    child.setOffset(currentOffset + findTargetOffset(item, child.getRedefines()));
                    computeOffsets(child, child.getOffset());
                    continue;
                }
                int childSize = computeOffsets(child, currentOffset + groupLength);
                if (child.hasOccurs()) childSize *= child.getOccursMax();
                groupLength += childSize;
            }
            item.setLength(groupLength);
            return groupLength;
        } else {
            return item.getLength() > 0 ? item.getLength() : 0;
        }
    }

    private int findTargetOffset(CobolDataItem parent, String targetName) {
        for (CobolDataItem child : parent.getChildren()) {
            if (child.getName().equals(targetName)) {
                return child.getOffset() - parent.getOffset();
            }
        }
        return 0;
    }
}
