package com.copybook.generator;

import com.copybook.parser.model.CobolCopybook;
import com.copybook.parser.model.CobolDataItem;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects structurally identical group definitions across a copybook
 * so they can be generated once and reused via import.
 * <p>
 * Uses a normalized structural signature (hash) of each group's children
 * to identify duplicates. Two groups are "shared" if their child layout
 * (levels, PICs, USAGEs, occurs metadata, order) is identical — regardless
 * of whether their own names match.
 */
public class SharedStructureDetector {

    /**
     * Maps a structural signature to the canonical class name and all usage locations.
     */
    public record SharedStructure(
            String canonicalClassName,
            String signature,
            CobolDataItem canonicalDefinition,
            List<UsageLocation> usages
    ) {
    }

    public record UsageLocation(
            String parentRecordName,
            String fieldName,
            CobolDataItem item
    ) {
    }

    /**
     * Scans the entire copybook and returns a map of signature -> SharedStructure
     * for all groups that appear more than once.
     */
    public Map<String, SharedStructure> detect(CobolCopybook copybook) {
        // First pass: collect all group items and their signatures
        Map<String, List<GroupEntry>> signatureGroups = new LinkedHashMap<>();

        for (CobolDataItem record : copybook.getRecords()) {
            collectGroups(record, record.getName(), signatureGroups);
        }

        // Second pass: keep only signatures that appear >= 2 times
        Map<String, SharedStructure> shared = new LinkedHashMap<>();

        for (var entry : signatureGroups.entrySet()) {
            List<GroupEntry> groups = entry.getValue();
            if (groups.size() >= 2) {
                // Use the first occurrence as canonical
                GroupEntry canonical = groups.get(0);
                String className = NamingConverter.toPascalCase(canonical.item.getName());

                List<UsageLocation> usages = new ArrayList<>();
                for (GroupEntry g : groups) {
                    usages.add(new UsageLocation(g.parentRecordName, g.item.getName(), g.item));
                }

                shared.put(entry.getKey(), new SharedStructure(
                        className, entry.getKey(), canonical.item, usages
                ));
            }
        }

        return shared;
    }

    /**
     * Checks whether a given group item is a shared structure.
     */
    public boolean isShared(CobolDataItem item, Map<String, SharedStructure> sharedMap) {
        if (!item.isGroup()) return false;
        String sig = computeSignature(item);
        return sharedMap.containsKey(sig);
    }

    /**
     * Returns the canonical class name for a shared group, or null if not shared.
     */
    public String getSharedClassName(CobolDataItem item, Map<String, SharedStructure> sharedMap) {
        if (!item.isGroup()) return null;
        String sig = computeSignature(item);
        SharedStructure ss = sharedMap.get(sig);
        return ss != null ? ss.canonicalClassName() : null;
    }

    private void collectGroups(CobolDataItem item, String parentRecordName,
                               Map<String, List<GroupEntry>> signatureGroups) {
        if (item.isGroup() && item.getLevel() > 1) {
            String sig = computeSignature(item);
            signatureGroups.computeIfAbsent(sig, k -> new ArrayList<>())
                    .add(new GroupEntry(parentRecordName, item));
        }

        for (CobolDataItem child : item.getChildren()) {
            collectGroups(child, parentRecordName, signatureGroups);
        }
    }

    /**
     * Computes a normalized structural signature for a group item.
     * The signature is based on children order, levels, PICs, USAGEs,
     * occurs metadata, and redefines — but NOT the group's own name.
     */
    public String computeSignature(CobolDataItem group) {
        StringBuilder sb = new StringBuilder();
        buildSignatureString(group, sb);

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(sb.toString().getBytes());
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            // Fallback: use the string itself
            return sb.toString().hashCode() + "";
        }
    }

    private void buildSignatureString(CobolDataItem item, StringBuilder sb) {
        for (CobolDataItem child : item.getChildren()) {
            sb.append(child.getLevel()).append("|");
            sb.append(child.getPic() != null ? child.getPic().toUpperCase() : "").append("|");
            sb.append(child.getUsage().getCobolName()).append("|");
            sb.append(child.isSigned()).append("|");
            sb.append(child.getOccursMin()).append("-").append(child.getOccursMax()).append("|");
            sb.append(child.getDependingOn() != null ? child.getDependingOn() : "").append("|");
            sb.append(child.getRedefines() != null ? child.getRedefines() : "").append("|");

            if (child.isGroup()) {
                sb.append("{");
                buildSignatureString(child, sb);
                sb.append("}");
            }

            sb.append(";");
        }
    }

    private record GroupEntry(String parentRecordName, CobolDataItem item) {
    }
}
