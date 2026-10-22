       IDENTIFICATION DIVISION.
       PROGRAM-ID. LOADXREF.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT IN-FILE ASSIGN TO INFILE
                  ORGANIZATION IS SEQUENTIAL
                  FILE STATUS IS IN-STATUS.
           SELECT KSDS-FILE ASSIGN TO XREFFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS KSDS-KEY
                  FILE STATUS  IS KSDS-STATUS.
       DATA DIVISION.
       FILE SECTION.
       FD  IN-FILE RECORD CONTAINS 50 CHARACTERS.
       01  IN-REC                PIC X(50).
       FD  KSDS-FILE.
       01  KSDS-REC.
           05 KSDS-KEY           PIC X(16).
           05 KSDS-DATA          PIC X(34).
       WORKING-STORAGE SECTION.
       01  IN-STATUS             PIC XX.
       01  KSDS-STATUS           PIC XX.
       01  WS-COUNT              PIC 9(9) VALUE 0.
       PROCEDURE DIVISION.
           OPEN INPUT IN-FILE OUTPUT KSDS-FILE
           IF IN-STATUS NOT = '00' OR KSDS-STATUS NOT = '00'
              DISPLAY 'LOADXREF OPEN FAILED ' IN-STATUS ' ' KSDS-STATUS
              STOP RUN RETURNING 12
           END-IF
           PERFORM UNTIL IN-STATUS NOT = '00'
              READ IN-FILE
              IF IN-STATUS = '00'
                 WRITE KSDS-REC FROM IN-REC
                 IF KSDS-STATUS NOT = '00'
                    DISPLAY 'LOADXREF WRITE FAILED ' KSDS-STATUS
                    STOP RUN RETURNING 12
                 END-IF
                 ADD 1 TO WS-COUNT
              END-IF
           END-PERFORM
           CLOSE IN-FILE KSDS-FILE
           DISPLAY 'LOADXREF RECORDS LOADED: ' WS-COUNT
           STOP RUN.
