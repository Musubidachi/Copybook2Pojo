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
          05 ITEM-DETAIL OCCURS 0 TO 10 TIMES
             DEPENDING ON ITEM-COUNT.
             10 ITEM-ID               PIC X(5).
             10 ITEM-AMOUNT           PIC 9(5)V99.
          05 RELEASE-REL              PIC 9(6) COMP.
          05 RELEASE-REL-D            PIC 9(2) COMP.
