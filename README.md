# COBOL Copybook to Java Domain (POJO) Conversion Guide

## Purpose

This document describes the intended behavior for converting a **COBOL copybook** into a **Java domain model (POJO set)**. The goal is to preserve COBOL structure and semantics as faithfully as practical while producing Java classes that are readable, typed, and maintainable.

The generated output should support:

- Standard POJO generation using **Lombok**
- Multiple `01` records in a single copybook
- A wrapper/root POJO when multiple `01` records are present
- Java-friendly field names while preserving original COBOL names in comments/annotations
- Support for:
  - `OCCURS`
  - `OCCURS DEPENDING ON`
  - `REDEFINES`
  - `COMP`, `COMP-1`, `COMP-2`, `COMP-3`
  - `PIC X`
  - `PIC 9`
  - signed and unsigned numerics
  - implied decimal `V`
  - level `66`
  - level `77`
  - level `88`
  - indexes / subscripts metadata
  - `BigDecimal`
  - `List`
  - `LocalDate`
  - `YearMonth`
  - enums
  - fixed-length string handling

---

## Design Goals

1. **Preserve COBOL meaning**
   - Maintain original field structure, usage, and constraints.
   - Keep original COBOL field names visible in generated metadata/comments.

2. **Generate idiomatic Java**
   - Use camelCase Java field names.
   - Prefer wrapper types where nullability or optionality makes sense.
   - Use strong Java types when COBOL intent is recognizable.

3. **Retain traceability**
   - Every generated Java field should include metadata reflecting the original COBOL definition:
     - COBOL field name
     - level
     - PIC clause
     - USAGE / COMP type
     - signed/unsigned
     - occurs metadata
     - redefines metadata
     - offset/length when available

4. **Detect and reuse shared structures**
   - Repeated or referenced COBOL group layouts should be identified and generated once.
   - Shared structures from copy members or duplicated group definitions should become reusable Java classes imported where needed.

5. **Support domain-friendly typing**
   - Dates should become `LocalDate` or `YearMonth` when the copybook pattern clearly implies them.
   - Condition names (`88`) should be represented as enums where appropriate.
   - Repeating groups should become `List<T>` where appropriate.

---

## Output Structure

### 1. One Java class per `01` record

Each COBOL `01` record must generate its own Java class.

Example:

```cobol
01 CUSTOMER-REC.
   05 CUST-ID              PIC 9(9).
01 ORDER-REC.
   05 ORDER-ID             PIC X(10).
```

Generated conceptually as:

```java
public class CustomerRec { ... }
public class OrderRec { ... }
```

### 2. Wrapper POJO for multiple `01` records

If the copybook contains multiple `01` records, generate a wrapper/root class that contains references to each generated `01` class.

Example conceptually:

```java
public class CopybookRoot {
    private CustomerRec customerRec;
    private OrderRec orderRec;
}
```

This wrapper is for organizational purposes and does **not** imply all `01` records coexist in the same physical memory layout unless explicitly required by the parser/runtime.

---

## Lombok Usage

Generated classes should use Lombok to reduce boilerplate.

Recommended annotations:

```java
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
```

Default recommendation:

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
```

Use `@Builder` where construction convenience is useful. It may be omitted for union/redefines classes if it complicates usage.

---

## Naming Rules

### COBOL name preservation
- Preserve the exact COBOL field name in metadata/comments.
- Example: `CUSTOMER-NUMBER` remains visible in annotations/comments.

### Java field naming
- Convert COBOL names to Java camelCase.
- Example:
  - COBOL: `CUSTOMER-NUMBER`
  - Java: `customerNumber`

### Java class naming
- Convert group names / `01` names to PascalCase.
- Example:
  - COBOL: `CUSTOMER-REC`
  - Java: `CustomerRec`

---

## Field-Level Metadata Requirement

The generated Java field should include the COBOL field name **above the field**, along with metadata for traceability.

Recommended style:

```java
/**
 * COBOL Name: CUSTOMER-NUMBER
 * Level: 05
 * PIC: 9(9)
 * Usage: DISPLAY
 * Signed: false
 * Offset: 12
 * Length: 9
 */
```

In addition, field annotations should mirror COBOL constraints.

Example custom annotation approach:

```java
@CobolField(
    name = "CUSTOMER-NUMBER",
    level = 5,
    pic = "9(9)",
    usage = "DISPLAY",
    signed = false,
    offset = 12,
    length = 9
)
```

---

## Suggested Java Annotations

Because you asked for annotations that mirror COBOL limitations, the generator should emit field-level annotations such as:

- `@CobolField(...)`
- `@CobolOccurs(...)`
- `@CobolRedefines(...)`
- `@CobolConditionNames(...)`
- `@CobolDateFormat(...)`
- `@Size(...)`
- `@Digits(...)`
- `@Pattern(...)`

### Example

```java
@CobolField(name = "ACCOUNT-NO", level = 5, pic = "X(12)", usage = "DISPLAY", length = 12)
@Size(max = 12)
private String accountNo;
```

```java
@CobolField(name = "AMOUNT-DUE", level = 5, pic = "S9(7)V99", usage = "COMP-3", signed = true)
@Digits(integer = 7, fraction = 2)
private BigDecimal amountDue;
```

These annotations may be custom annotations, Jakarta Validation annotations, or both.

---

## Type Mapping Rules

## PIC X

### Rule
`PIC X(...)` should map to `String`.

### Notes
- Preserve declared length in metadata/annotations.
- Support fixed-length string helper behavior for trimming/padding.
- If length is `1`, still prefer `String` unless the generator is explicitly configured for `Character`.

Example:

```cobol
05 LAST-NAME               PIC X(20).
```

```java
@CobolField(name = "LAST-NAME", pic = "X(20)", length = 20)
@Size(max = 20)
private String lastName;
```

---

## PIC 9 and DISPLAY numeric

### Unsigned integer
```cobol
05 ITEM-COUNT              PIC 9(5).
```

Recommended Java type:
- `Integer` for smaller ranges
- `Long` for larger ranges
- `BigDecimal` if a consistent numeric policy is preferred

### Signed integer
```cobol
05 BALANCE-SIGN            PIC S9(5).
```

Recommended Java type:
- `Integer`, `Long`, or `BigDecimal` depending on size/policy

Use wrapper types when nullability or absence matters.

---

## Decimal / implied decimal (`V`)

### Rule
Any field with implied decimal (`V`) should map to `BigDecimal`.

Example:

```cobol
05 RATE                    PIC 9(3)V99.
05 AMOUNT                  PIC S9(7)V99.
```

Generated conceptually:

```java
private BigDecimal rate;
private BigDecimal amount;
```

Use:

```java
@Digits(integer = 3, fraction = 2)
private BigDecimal rate;
```

---

## COMP / BINARY

### Rule
Binary numeric fields (`COMP`, `BINARY`, `COMP-4` if later added) should map based on storage size and intended range.

Recommended mapping:
- small binary values -> `Integer`
- larger binary values -> `Long`
- if decimal implied or business precision matters -> `BigDecimal`

Preserve usage in metadata.

---

## COMP-1 and COMP-2

### Rule
- `COMP-1` -> `Float`
- `COMP-2` -> `Double`

Example:

```cobol
05 INTEREST-FACTOR         USAGE COMP-1.
05 HIGH-PRECISION-RATE     USAGE COMP-2.
```

```java
private Float interestFactor;
private Double highPrecisionRate;
```

If PIC is omitted for these fields, store whatever usage metadata is available.

---

## COMP-3 (Packed Decimal)

### Rule
Packed decimal fields should generally map to `BigDecimal`.

Examples:

```cobol
05 TOTAL-AMOUNT            PIC S9(9)V99 COMP-3.
05 QUANTITY                PIC 9(5) COMP-3.
```

Generated conceptually:

```java
private BigDecimal totalAmount;
private BigDecimal quantity;
```

Even integer-looking packed decimals may still be modeled as `BigDecimal` for consistency and lossless conversion.

---

## Signed vs Unsigned

Signedness must be captured in metadata and respected in validation/serialization logic.

Examples:
- `S9(5)` -> signed numeric
- `9(5)` -> unsigned numeric

For numeric wrappers, signedness does not require a different Java type, but it should be preserved in:
- comments
- annotations
- parser/formatter logic

---

## Date Recognition Rules

## `REL` / `REL-D` pairing

You specified this rule:

- Adjacent `REL` + `REL-D` fields should be evaluated as a `LocalDate` pair
- If date intent is inferred from the comp/format, a field may instead become `YearMonth`

### LocalDate rule
When related fields appear next to each other and follow the `REL` / `REL-D` pattern, combine or semantically represent them as `LocalDate` when the copybook definition indicates full date precision.

### YearMonth rule
When the underlying numeric representation and comp characteristics indicate month precision without day precision, map to `YearMonth`.

### Recommended annotation
```java
@CobolDateFormat(type = CobolDateType.LOCAL_DATE)
private LocalDate releaseDate;
```

or

```java
@CobolDateFormat(type = CobolDateType.YEAR_MONTH)
private YearMonth releasePeriod;
```

### Important implementation note
The generator should inspect:
- field adjacency
- naming pattern
- PIC size
- COMP/usage
- total precision

before deciding between:
- `LocalDate`
- `YearMonth`
- plain numeric/string fallback

If intent is ambiguous, prefer preserving the raw field unless configured otherwise.

---

## Level 88 Condition Names

You requested that level `88` condition names be handled as **Java enums**.

### Rule
A field with associated `88` values should generate:
1. the base storage field
2. a Java enum representing condition-name values
3. optional helper conversion methods

Example:

```cobol
05 STATUS-CODE             PIC X(1).
   88 STATUS-ACTIVE        VALUE 'A'.
   88 STATUS-INACTIVE      VALUE 'I'.
```

Generated conceptually:

```java
private String statusCode;
private StatusCode statusCodeEnum;

public enum StatusCode {
    ACTIVE("A"),
    INACTIVE("I");
}
```

### Recommended behavior
- Keep the raw field for fidelity
- Optionally expose the enum view
- Map multi-value `88`s carefully
- If the `88` values overlap or are non-exclusive, document that the enum is interpretive, not authoritative

### Annotation example
```java
@CobolConditionNames({
    @CobolConditionName(name = "STATUS-ACTIVE", value = "A"),
    @CobolConditionName(name = "STATUS-INACTIVE", value = "I")
})
private String statusCode;
```

---

## Level 66

Level `66` entries represent `RENAMES`.

### Rule
Because Java fields do not support COBOL memory renaming semantics directly, treat level `66` as metadata or derived view definitions.

Recommended handling:
- Preserve level `66` definitions in generated comments/annotations
- Optionally generate helper getter methods or view objects
- Do not generate a normal independent storage field unless configured to do so

Example strategy:
```java
public String getRenamedRangeView() { ... }
```

---

## Level 77

Level `77` should be treated as a standalone elementary item.

### Rule
Generate it as a normal field on the owning class or in a standalone holder class, depending on parser architecture.

---

## OCCURS

### Fixed OCCURS

Example:

```cobol
05 PHONE-NUMBER            PIC X(10) OCCURS 3 TIMES.
```

Generate as:

```java
private List<String> phoneNumber;
```

or, if configured for fixed arrays:
```java
private List<String> phoneNumber;
```

`List` is preferred per your request.

Annotation example:

```java
@CobolOccurs(min = 3, max = 3)
private List<String> phoneNumber;
```

### OCCURS on groups

If a group occurs multiple times, generate a child class for the group and use `List<ChildType>`.

Example:

```cobol
05 ADDRESS-GROUP OCCURS 5 TIMES.
   10 STREET               PIC X(30).
   10 ZIP-CODE             PIC 9(5).
```

Generate conceptually:

```java
private List<AddressGroup> addressGroup;
```

---

## OCCURS DEPENDING ON

You requested support for nested `OCCURS DEPENDING ON`.

### Rule
Generate `List<T>` and preserve dependency metadata.

Example:

```cobol
05 ITEM-COUNT              PIC 9(3).
05 ITEM-DETAIL OCCURS 0 TO 50 TIMES DEPENDING ON ITEM-COUNT.
   10 ITEM-ID              PIC X(10).
```

Generated conceptually:

```java
@CobolOccurs(dependingOn = "ITEM-COUNT", min = 0, max = 50)
private List<ItemDetail> itemDetail;
```

### Nested ODO
For nested `OCCURS DEPENDING ON`:
- generate nested `List` fields normally
- retain the dependent field in metadata
- document any runtime parsing dependency clearly
- parser must resolve parent counts before child counts

---

## Subscripts / Indexes

COBOL subscripts and indexes are not normally persisted as business fields in Java, but they are relevant metadata.

### Rule
- Preserve index/subscript details in comments/annotations
- Do not generate dedicated Java fields for indexes unless explicitly required by runtime parsing logic

Example metadata:
```java
@CobolOccurs(indexedBy = {"ITEM-IDX"})
private List<ItemDetail> itemDetail;
```

---

## REDEFINES

You requested **wrapper/union-style model** support for `REDEFINES`.

### Rule
`REDEFINES` should generate a wrapper or union-style representation rather than flattening blindly.

Example:

```cobol
05 CUSTOMER-DATA.
   10 CUSTOMER-ID          PIC 9(9).
05 CUSTOMER-DATA-RAW REDEFINES CUSTOMER-DATA PIC X(9).
```

Suggested generated model:

```java
private CustomerDataRedefines customerDataRedefines;
```

Where the wrapper conceptually contains views of the same underlying storage:

```java
public class CustomerDataRedefines {
    private CustomerData customerData;
    private String customerDataRaw;
}
```

### Guidance
- Preserve the target of the redefine
- Preserve byte length compatibility
- Avoid pretending both fields are fully independent
- Document that these are alternate views over the same COBOL storage region

### Annotation example
```java
@CobolRedefines(target = "CUSTOMER-DATA")
private String customerDataRaw;
```

---

## FILLER

You requested that filler fields be ignored.

### Rule
- Do not generate Java fields for `FILLER`
- Preserve filler ranges only if offsets/length calculations require them internally
- Optionally include them in debug metadata if needed by the parser generator

---

## Fixed-Length String Support

You requested fixed-length string support.

### Rule
For `PIC X(n)` fields:
- preserve declared length
- generate metadata for padding/trimming
- support serializer/deserializer helpers that:
  - pad with spaces to target length
  - trim trailing spaces when reading, if configured

Suggested annotation:

```java
@CobolFixedLength(length = 20, padChar = ' ', trimOnRead = true)
private String lastName;
```

---

## BigDecimal Guidance

Use `BigDecimal` for:
- any field with implied decimal `V`
- most `COMP-3` packed decimals
- large precision business numerics
- ambiguous decimal business amounts

This is the safest choice for financial and business-domain values.

---

## Wrapper Types vs Primitives

You requested wrapper types when it makes sense.

### Recommendation
Prefer:
- `Integer` instead of `int`
- `Long` instead of `long`
- `Float` instead of `float`
- `Double` instead of `double`

Reasons:
- nullable when source data is absent/unset
- better fit for partial parsing workflows
- safer for optional/redefined data

Primitives may be used only when the generator is explicitly configured for non-nullable output.

---

## Example Conversion

### COBOL
```cobol
01 CUSTOMER-REC.
   05 CUSTOMER-ID              PIC 9(9).
   05 CUSTOMER-NAME            PIC X(30).
   05 CUSTOMER-STATUS          PIC X(1).
      88 STATUS-ACTIVE         VALUE 'A'.
      88 STATUS-INACTIVE       VALUE 'I'.
   05 MONTHLY-LIMIT            PIC S9(7)V99 COMP-3.
   05 PHONE-ENTRY OCCURS 3 TIMES.
      10 PHONE-NUMBER          PIC X(10).
   05 ITEM-COUNT               PIC 9(2) COMP.
   05 ITEM-DETAIL OCCURS 0 TO 10 TIMES DEPENDING ON ITEM-COUNT.
      10 ITEM-ID               PIC X(5).
      10 ITEM-AMOUNT           PIC 9(5)V99.
   05 RELEASE-REL              PIC 9(6) COMP.
   05 RELEASE-REL-D            PIC 9(2) COMP.
```

### Conceptual Java
```java
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerRec {

    /**
     * COBOL Name: CUSTOMER-ID
     * Level: 05
     * PIC: 9(9)
     * Usage: DISPLAY
     */
    @CobolField(name = "CUSTOMER-ID", level = 5, pic = "9(9)", usage = "DISPLAY")
    private Integer customerId;

    /**
     * COBOL Name: CUSTOMER-NAME
     * Level: 05
     * PIC: X(30)
     * Usage: DISPLAY
     */
    @CobolField(name = "CUSTOMER-NAME", level = 5, pic = "X(30)", usage = "DISPLAY", length = 30)
    @CobolFixedLength(length = 30, padChar = ' ', trimOnRead = true)
    private String customerName;

    /**
     * COBOL Name: CUSTOMER-STATUS
     * Level: 05
     * PIC: X(1)
     * Usage: DISPLAY
     */
    @CobolField(name = "CUSTOMER-STATUS", level = 5, pic = "X(1)", usage = "DISPLAY", length = 1)
    @CobolConditionNames({
        @CobolConditionName(name = "STATUS-ACTIVE", value = "A"),
        @CobolConditionName(name = "STATUS-INACTIVE", value = "I")
    })
    private String customerStatus;

    private CustomerStatus customerStatusEnum;

    /**
     * COBOL Name: MONTHLY-LIMIT
     * Level: 05
     * PIC: S9(7)V99
     * Usage: COMP-3
     */
    @CobolField(name = "MONTHLY-LIMIT", level = 5, pic = "S9(7)V99", usage = "COMP-3", signed = true)
    @Digits(integer = 7, fraction = 2)
    private BigDecimal monthlyLimit;

    @CobolOccurs(min = 3, max = 3)
    private List<PhoneEntry> phoneEntry;

    @CobolField(name = "ITEM-COUNT", level = 5, pic = "9(2)", usage = "COMP")
    private Integer itemCount;

    @CobolOccurs(min = 0, max = 10, dependingOn = "ITEM-COUNT")
    private List<ItemDetail> itemDetail;

    @CobolDateFormat(type = CobolDateType.LOCAL_DATE)
    private LocalDate releaseDate;

    public enum CustomerStatus {
        ACTIVE("A"),
        INACTIVE("I");

        private final String value;

        CustomerStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
```

---

## Suggested Generator Rules Summary

| COBOL construct | Java output |
|---|---|
| `01` record | One Java class |
| multiple `01`s | wrapper/root POJO containing each `01` class |
| group item | nested class or referenced child class |
| `PIC X(n)` | `String` |
| `PIC 9(n)` | `Integer` / `Long` / `BigDecimal` depending on size |
| `S9(...)` | signed numeric wrapper |
| `V` implied decimal | `BigDecimal` |
| `COMP` | `Integer` / `Long` / `BigDecimal` |
| `COMP-1` | `Float` |
| `COMP-2` | `Double` |
| `COMP-3` | `BigDecimal` |
| `OCCURS` | `List<T>` |
| `OCCURS DEPENDING ON` | `List<T>` + dependency annotation |
| nested ODO | nested `List<T>` + dependency metadata |
| `REDEFINES` | wrapper/union-style model |
| `88` | enum + raw field metadata |
| `66` | metadata / derived view / helper |
| `77` | standalone field |
| filler | ignored |
| subscripts/indexes | metadata only |
| `REL` + `REL-D` pair | infer `LocalDate` |
| qualifying comp/date layout | infer `YearMonth` or `LocalDate` |
| repeated identical groups / copy member expansions | generate one shared class and reuse/import it |

---

## Recommended Custom Annotation Set

A practical annotation set for the generated code could include:

```java
@interface CobolField {
    String name();
    int level();
    String pic() default "";
    String usage() default "DISPLAY";
    boolean signed() default false;
    int offset() default -1;
    int length() default -1;
}

@interface CobolOccurs {
    int min() default 0;
    int max() default 0;
    String dependingOn() default "";
    String[] indexedBy() default {};
}

@interface CobolRedefines {
    String target();
}

@interface CobolFixedLength {
    int length();
    char padChar() default ' ';
    boolean trimOnRead() default true;
}

@interface CobolConditionName {
    String name();
    String value();
}

@interface CobolConditionNames {
    CobolConditionName[] value();
}

@interface CobolDateFormat {
    CobolDateType type();
}

enum CobolDateType {
    LOCAL_DATE,
    YEAR_MONTH
}
```

---

## Implementation Notes

1. **Do not lose raw COBOL semantics**
   - Especially for `REDEFINES`, `OCCURS DEPENDING ON`, and `88`s.

2. **Prefer metadata-rich generation**
   - The more traceable the generated code is, the easier it is to validate and debug.

3. **Do not over-infer**
   - If the generator cannot confidently determine date semantics, preserve the raw representation and annotate why.

4. **Keep parser and model concerns separate**
   - The POJO should model the data.
   - Parsing/serialization rules may live in companion classes or adapters.

5. **Preserve offsets and physical layout details when available**
   - Especially useful when round-tripping back to COBOL-format records.

---


## Shared Class / Reusable Structure Detection

You also want the generator to identify **referenced or duplicated classes/structures** so they are generated once and then reused.

### Rule
If a copybook:
- includes a copy member that expands to a reusable group, or
- defines the same logical data group under multiple `01` records, or
- repeats an identical group structure in multiple places,

the generator should:

1. detect that the structure is the same or intentionally shared
2. generate the Java class for that structure only once
3. reference it from the owning classes as a normal object field
4. import and reuse the generated class rather than duplicating it

### Examples of reusable structures

#### Copy member reuse
If multiple records include the same copy member content, the resulting Java type should be generated once and reused everywhere it appears.

#### Duplicate group reuse
If two separate `01` records contain the same nested group definition, the generator should detect the structural match and produce one shared Java class instead of two duplicate classes.

### Matching guidance
A reusable/shared structure can be identified using a combination of:

- normalized COBOL group name
- normalized child layout
- child order
- levels
- PIC clauses
- USAGE/COMP metadata
- occurs metadata
- redefines metadata
- signedness / decimal characteristics

The matching should be **structure-based**, not just name-based.

### Preferred behavior
- If two groups are structurally identical, generate one shared class.
- If two groups have the same name but different layout, generate separate classes and disambiguate names.
- If two groups have different names but identical layout, either:
  - generate one shared class based on a canonical name, or
  - generate one primary class and alias/reference it in metadata.

### Example concept

COBOL:
```cobol
01 CUSTOMER-REC.
   05 ADDRESS-GROUP.
      10 STREET              PIC X(30).
      10 ZIP-CODE            PIC 9(5).

01 VENDOR-REC.
   05 ADDRESS-GROUP.
      10 STREET              PIC X(30).
      10 ZIP-CODE            PIC 9(5).
```

Preferred Java concept:
```java
public class AddressGroup {
    private String street;
    private Integer zipCode;
}

public class CustomerRec {
    private AddressGroup addressGroup;
}

public class VendorRec {
    private AddressGroup addressGroup;
}
```

### Import/reuse requirement
The generated classes must:
- import the shared class where needed
- avoid regenerating duplicate child/group classes
- maintain metadata indicating where the shared structure originated, if helpful

### Recommended metadata
It is useful to track:
- original source copy member name
- first defining record/group
- all usage locations
- structural signature/hash used for deduplication

### Suggested deduplication workflow
1. Parse all `01` records and expanded copy members.
2. Build a normalized structural signature for each group.
3. Compare signatures across the entire copybook model.
4. Generate shared classes once.
5. Replace duplicates with references to the shared generated class.
6. Import the shared class in each owning Java class.

### Important caution
Do not merge groups solely because their names match. Merge only when:
- the structure is identical, or
- configuration explicitly states that the groups are intended to share a type.

If the structure differs even slightly, generate separate Java classes.

---

# When in doubt..

The safest default behavior is:

- preserve as much COBOL metadata as possible
- generate one class per `01`
- use a wrapper POJO for multiple `01` records
- use Lombok
- prefer wrapper numeric types
- use `BigDecimal` for decimals and packed decimals
- use `List` for all `OCCURS`
- model `REDEFINES` as union/wrapper-style
- generate enums for `88`
- ignore filler
- detect repeated/referenced shared structures and generate them once
- import and reuse shared generated classes instead of duplicating them
- emit field-level comments and annotations for COBOL traceability
- infer `LocalDate` and `YearMonth` only when the copybook pattern is strong enough