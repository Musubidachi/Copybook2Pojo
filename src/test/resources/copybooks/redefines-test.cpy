       01 PAYMENT-REC.
          05 PAYMENT-TYPE             PIC X(1).
             88 PAY-CREDIT            VALUE 'C'.
             88 PAY-DEBIT             VALUE 'D'.
             88 PAY-CHECK             VALUE 'K'.
          05 CUSTOMER-DATA.
             10 CUSTOMER-ID           PIC 9(9).
          05 CUSTOMER-DATA-RAW REDEFINES CUSTOMER-DATA
                                      PIC X(9).
          05 INTEREST-FACTOR          USAGE COMP-1.
          05 HIGH-PRECISION-RATE      USAGE COMP-2.
          05 BALANCE-SIGN             PIC S9(5).
          05 FILLER                   PIC X(10).
          05 ACCOUNT-NO               PIC X(12).

       77 GLOBAL-COUNTER              PIC 9(5) COMP.
