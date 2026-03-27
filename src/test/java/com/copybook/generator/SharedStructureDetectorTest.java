package com.copybook.generator;

import com.copybook.parser.CopybookParser;
import com.copybook.parser.model.CobolCopybook;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SharedStructureDetectorTest {

    @Test
    void shouldDetectIdenticalGroupStructures() throws IOException {
        CopybookParser parser = new CopybookParser();
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/multi-record.cpy"));

        SharedStructureDetector detector = new SharedStructureDetector();
        Map<String, SharedStructureDetector.SharedStructure> shared = detector.detect(copybook);

        // ADDRESS-GROUP appears identically in both CUSTOMER-REC and VENDOR-REC
        assertThat(shared).isNotEmpty();

        // Verify there's a shared structure for AddressGroup
        boolean foundAddressGroup = shared.values().stream()
                .anyMatch(s -> s.canonicalClassName().equals("AddressGroup"));
        assertThat(foundAddressGroup).isTrue();

        // Should have 2 usage locations
        SharedStructureDetector.SharedStructure addressGroup = shared.values().stream()
                .filter(s -> s.canonicalClassName().equals("AddressGroup"))
                .findFirst().orElse(null);

        assertThat(addressGroup).isNotNull();
        assertThat(addressGroup.usages()).hasSize(2);
    }

    @Test
    void shouldNotDetectSharedWhenSingleOccurrence() throws IOException {
        CopybookParser parser = new CopybookParser();
        CobolCopybook copybook = parser.parse(
                Path.of("src/test/resources/copybooks/customer-rec.cpy"));

        SharedStructureDetector detector = new SharedStructureDetector();
        Map<String, SharedStructureDetector.SharedStructure> shared = detector.detect(copybook);

        // No duplicated group structures in customer-rec.cpy
        assertThat(shared).isEmpty();
    }
}
