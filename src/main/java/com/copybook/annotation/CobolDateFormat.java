package com.copybook.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a field has been inferred as a date from the COBOL
 * copybook pattern (e.g. REL/REL-D pairing) and specifies the
 * target Java date type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CobolDateFormat {

    /** The inferred Java date type. */
    CobolDateType type();
}
