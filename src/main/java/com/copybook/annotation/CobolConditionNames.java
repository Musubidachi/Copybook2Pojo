package com.copybook.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for multiple {@link CobolConditionName} annotations,
 * representing all level-88 condition names associated with a field.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CobolConditionNames {

    CobolConditionName[] value();
}
