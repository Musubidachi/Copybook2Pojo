package com.copybook.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Represents a single COBOL level-88 condition name and its value.
 * Used as a repeatable element within {@link CobolConditionNames}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface CobolConditionName {

    /** The COBOL condition name (e.g. "STATUS-ACTIVE"). */
    String name();

    /** The condition value (e.g. "A"). */
    String value();
}
