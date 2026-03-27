package com.copybook.annotation;

/**
 * Enumerates the Java date types that a COBOL date field may map to.
 */
public enum CobolDateType {

    /** Maps to {@link java.time.LocalDate}. */
    LOCAL_DATE,

    /** Maps to {@link java.time.YearMonth}. */
    YEAR_MONTH
}
