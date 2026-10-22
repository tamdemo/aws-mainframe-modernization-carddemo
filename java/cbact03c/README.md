# CBACT03C – Java port

Java port of the batch program `app/cbl/CBACT03C.cbl` ("read and print account
cross reference data file"), run by `app/jcl/READXREF.jcl`.

The port behaves exactly like the COBOL, so the two outputs can be compared
line by line. **That includes the known bug where every record is printed
twice**: once in `1000-XREFFILE-GET-NEXT` and again in the main loop. The bug
is kept on purpose and will be fixed in a separate change.

## Layout

| Java | COBOL source |
|------|--------------|
| `CardXrefRecord` | copybook `app/cpy/CVACT03Y.cpy` (`CARD-XREF-RECORD`, 50 bytes) |
| `XrefFile` | `SELECT XREFFILE-FILE` / `FD XREFFILE-FILE` and its file status codes |
| `Cbact03c.run()` | `PROCEDURE DIVISION` mainline |
| `Cbact03c.xrefFileGetNext()` | `1000-XREFFILE-GET-NEXT` |
| `Cbact03c.xrefFileOpen()` | `0000-XREFFILE-OPEN` |
| `Cbact03c.xrefFileClose()` | `9000-XREFFILE-CLOSE` |
| `Cbact03c.abendProgram()` / `AbendException` | `9999-ABEND-PROGRAM` / `CALL 'CEE3ABD'` |
| `Cbact03c.displayIoStatus()` | `9910-DISPLAY-IO-STATUS` |

Each Java section has a comment naming the COBOL paragraph it ports.

## Running

```bash
mvn package
java -jar target/cbact03c.jar --xreffile=../../app/data/EBCDIC/AWS.M2.CARDDEMO.CARDXREF.PS
```

Options:

- `--xreffile=<path>`: the input file, which takes the place of the `XREFFILE`
  DD statement. You can also set the `DD_XREFFILE` or `XREFFILE` environment
  variable instead.
- `--encoding=<charset>`: the file's character encoding. Default `IBM037`
  (EBCDIC). Use `US-ASCII` for ASCII files.
- `--format=FIXED|LINE`: the record format. `FIXED` (default) reads 50-byte
  records with no line breaks, as in a mainframe unload. `LINE` reads one
  record per line and pads each to 50 characters, as in
  `app/data/ASCII/cardxref.txt`.

Exit codes: `0` on success. If the file can't be opened, read or closed, the
program does what the COBOL does at that point: it prints the same error
lines, and then exits with code `231`. That is the COBOL abend code 999
(`U0999`) truncated to a single byte, which is all a JVM exit code can carry.

## Verifying parity with the COBOL

- `mvn test` runs the unit tests. These compare the Java output with SYSOUT
  captured from the original COBOL (compiled with GnuCOBOL 3.1), stored in
  `src/test/resources/golden/`. They also cover the duplicate print, an empty
  file, a missing file, a short record, and keys out of order.
- `parity/run-parity.sh` compiles the unchanged `CBACT03C.cbl` with GnuCOBOL.
  It loads the sample data into an indexed file using `parity/LOADXREF.cbl`, a
  test-only loader, then runs both the COBOL and the Java versions and diffs
  their output. The script needs `cobc` and a JDK 17 or later.

## Notes

- All fields are kept as fixed-width strings, not converted to numbers. The
  `PIC 9` fields are plain character digits, and `DISPLAY` prints them
  unchanged, so this keeps the output byte-for-byte the same. Numeric getters
  (`getCustIdValue()`, `getAcctIdValue()`) are provided for later use.
- The card number (`XREF-CARD-NUM`) is a full card number (PAN) and is printed
  unmasked, exactly as the COBOL prints it. Treat the job output as cardholder
  data under PCI DSS and personal data under GDPR, until masking is added as a
  later change.
