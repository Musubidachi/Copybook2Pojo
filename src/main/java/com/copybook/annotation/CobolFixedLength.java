package com.copybook.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a String field as having a COBOL fixed-length representation,
 * supporting padding and trimming behavior for serialization.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CobolFixedLength {

    /** Declared fixed length of the field. */
    int length();

    /** Character used for padding (default: space). */
    char padChar() default ' ';

    /** Whether to trim trailing pad characters on read (default: true). */
    boolean trimOnRead() default true;
}
