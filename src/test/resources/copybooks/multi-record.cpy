       01 CUSTOMER-REC.
          05 CUST-ID                  PIC 9(9).
          05 ADDRESS-GROUP.
             10 STREET                PIC X(30).
             10 ZIP-CODE              PIC 9(5).

       01 VENDOR-REC.
          05 VENDOR-ID                PIC 9(9).
          05 ADDRESS-GROUP.
             10 STREET                PIC X(30).
             10 ZIP-CODE              PIC 9(5).

       01 ORDER-REC.
          05 ORDER-ID                 PIC X(10).
          05 ORDER-AMOUNT             PIC S9(9)V99 COMP-3.
