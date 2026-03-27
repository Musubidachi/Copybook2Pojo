package com.copybook.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java field as originating from a COBOL data item, preserving
 * the original COBOL definition metadata for traceability.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CobolField {

    /** Original COBOL field name (e.g. "CUSTOMER-NUMBER"). */
    String name();

    /** COBOL level number (e.g. 5, 10, 77). */
    int level();

    /** PIC clause string (e.g. "9(9)", "X(30)", "S9(7)V99"). */
    String pic() default "";

    /** USAGE clause (e.g. "DISPLAY", "COMP", "COMP-3"). */
    String usage() default "DISPLAY";

    /** Whether the field is signed (PIC S9...). */
    boolean signed() default false;

    /** Byte offset within the record (-1 if unknown). */
    int offset() default -1;

    /** Byte length of the field (-1 if unknown). */
    int length() default -1;
}
