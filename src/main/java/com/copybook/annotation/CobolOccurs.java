package com.copybook.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Captures COBOL OCCURS metadata including fixed counts,
 * variable-length OCCURS DEPENDING ON, and index names.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CobolOccurs {

    /** Minimum number of occurrences (0 for variable-length). */
    int min() default 0;

    /** Maximum number of occurrences. */
    int max() default 0;

    /** COBOL field name referenced in DEPENDING ON clause (empty if fixed). */
    String dependingOn() default "";

    /** INDEXED BY names from the COBOL definition. */
    String[] indexedBy() default {};
}
