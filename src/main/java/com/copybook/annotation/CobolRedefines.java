package com.copybook.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that this field is a REDEFINES of another COBOL field,
 * meaning it occupies the same physical storage region.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CobolRedefines {

    /** The COBOL name of the target field being redefined. */
    String target();
}
