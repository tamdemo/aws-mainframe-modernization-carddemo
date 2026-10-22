package com.carddemo.batch.cbact03c;

import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Map;

/**
 * Java port of {@code app/cbl/CBACT03C.cbl}: "Read and print account cross reference data file".
 *
 * <p>Behaviour is kept identical to the COBOL so SYSOUT can be compared line by line. That
 * includes a COBOL bug: each record is printed twice, once in 1000-XREFFILE-GET-NEXT and once in
 * the main loop. Fix it in a separate change.
 *
 * <p>Each method is named after, and comments back to, the COBOL paragraph it replaces. The COBOL
 * working-storage flags (APPL-RESULT, END-OF-FILE, IO-STATUS) are kept as fields so the control
 * flow maps one-to-one.
 *
 * <p>Run (equivalent of {@code app/jcl/READXREF.jcl}):
 * <pre>
 *   java -jar cbact03c.jar --xreffile=AWS.M2.CARDDEMO.CARDXREF.PS [--encoding=IBM037] [--format=FIXED|LINE]
 * </pre>
 * The {@code XREFFILE} DD can also be given with env var {@code DD_XREFFILE} or {@code XREFFILE}
 * (same lookup as GnuCOBOL).
 */
public final class Cbact03c {

    static final String PROGRAM_ID = "CBACT03C";

    /** CEE3ABD ABCODE / TIMING used by 9999-ABEND-PROGRAM. */
    static final int ABEND_CODE = 999;
    static final int ABEND_TIMING = 0;

    /** Status byte 2 is converted with the z/OS code page, as TWO-BYTES-BINARY would see it. */
    static final Charset STATUS_CHARSET = Charset.forName("IBM037");

    // 01 APPL-RESULT PIC S9(9) COMP.  88 APPL-AOK VALUE 0.  88 APPL-EOF VALUE 16.
    private static final int APPL_AOK = 0;
    private static final int APPL_EOF = 16;

    private final XrefFile xrefFile;
    private final PrintStream sysout;

    // WORKING-STORAGE
    private CardXrefRecord cardXrefRecord;   // COPY CVACT03Y.
    private String xrefFileStatus = "  ";    // 01 XREFFILE-STATUS.
    private String ioStatus = "  ";          // 01 IO-STATUS.
    private int applResult;                  // 01 APPL-RESULT.
    private char endOfFile = 'N';            // 01 END-OF-FILE PIC X(01) VALUE 'N'.

    public Cbact03c(XrefFile xrefFile, PrintStream sysout) {
        this.xrefFile = xrefFile;
        this.sysout = sysout;
    }

    /**
     * PROCEDURE DIVISION mainline.
     *
     * @return RETURN-CODE at GOBACK (always 0 – the COBOL never sets it)
     * @throws AbendException where the COBOL would abend through 9999-ABEND-PROGRAM
     */
    public int run() {
        display("START OF EXECUTION OF PROGRAM " + PROGRAM_ID);
        xrefFileOpen();

        // PERFORM UNTIL END-OF-FILE = 'Y'
        while (endOfFile != 'Y') {
            if (endOfFile == 'N') {
                xrefFileGetNext();
                if (endOfFile == 'N') {
                    // Second print of the same record (the first is inside 1000-XREFFILE-GET-NEXT).
                    display(cardXrefRecord.toDisplayString());
                }
            }
        }

        xrefFileClose();

        display("END OF EXECUTION OF PROGRAM " + PROGRAM_ID);

        return 0; // GOBACK.
    }

    /** 1000-XREFFILE-GET-NEXT: READ XREFFILE-FILE INTO CARD-XREF-RECORD. */
    void xrefFileGetNext() {
        xrefFileStatus = xrefFile.read();
        if (xrefFileStatus.equals("00")) {
            cardXrefRecord = CardXrefRecord.fromRecord(xrefFile.getRecord());
            applResult = 0;
            // First print of the record (the original bug: the main loop prints it again).
            display(cardXrefRecord.toDisplayString());
        } else {
            if (xrefFileStatus.equals("10")) {
                applResult = 16;
            } else {
                applResult = 12;
            }
        }
        if (applResult == APPL_AOK) {
            // CONTINUE
        } else {
            if (applResult == APPL_EOF) {
                endOfFile = 'Y';
            } else {
                display("ERROR READING XREFFILE");
                ioStatus = xrefFileStatus;
                displayIoStatus();
                abendProgram();
            }
        }
    }

    /** 0000-XREFFILE-OPEN: OPEN INPUT XREFFILE-FILE. */
    void xrefFileOpen() {
        applResult = 8;
        xrefFileStatus = xrefFile.open();
        if (xrefFileStatus.equals("00")) {
            applResult = 0;
        } else {
            applResult = 12;
        }
        if (applResult == APPL_AOK) {
            // CONTINUE
        } else {
            display("ERROR OPENING XREFFILE");
            ioStatus = xrefFileStatus;
            displayIoStatus();
            abendProgram();
        }
    }

    /** 9000-XREFFILE-CLOSE: CLOSE XREFFILE-FILE. */
    void xrefFileClose() {
        applResult = 8;                // ADD 8 TO ZERO GIVING APPL-RESULT
        xrefFileStatus = xrefFile.close();
        if (xrefFileStatus.equals("00")) {
            applResult -= applResult;  // SUBTRACT APPL-RESULT FROM APPL-RESULT
        } else {
            applResult = 12;           // ADD 12 TO ZERO GIVING APPL-RESULT
        }
        if (applResult == APPL_AOK) {
            // CONTINUE
        } else {
            display("ERROR CLOSING XREFFILE");
            ioStatus = xrefFileStatus;
            displayIoStatus();
            abendProgram();
        }
    }

    /** 9999-ABEND-PROGRAM: CALL 'CEE3ABD' USING ABCODE, TIMING. */
    void abendProgram() {
        display("ABENDING PROGRAM");
        sysout.flush();
        throw new AbendException(ABEND_CODE, ABEND_TIMING);
    }

    /** 9910-DISPLAY-IO-STATUS. */
    void displayIoStatus() {
        display("FILE STATUS IS: NNNN" + formatIoStatus(ioStatus));
    }

    /**
     * Builds IO-STATUS-04 (PIC 9 + PIC 999) from the two-byte IO-STATUS, as 9910-DISPLAY-IO-STATUS
     * does.
     * <ul>
     *   <li>IO-STATUS not numeric, or IO-STAT1 = '9' (implementor-defined status): IO-STAT1
     *       followed by the binary value of IO-STAT2 as 3 digits (TWO-BYTES-BINARY / TWO-BYTES-RIGHT
     *       REDEFINES). Example: "9" + EBCDIC 'A' (X'C1') gives "9193".</li>
     *   <li>Otherwise: '0000' with IO-STATUS in positions 3:2. Example: "35" gives "0035".</li>
     * </ul>
     */
    static String formatIoStatus(String ioStatus) {
        char ioStat1 = ioStatus.charAt(0);
        char ioStat2 = ioStatus.charAt(1);
        if (!isNumeric(ioStatus) || ioStat1 == '9') {
            int twoBytesBinary = Byte.toUnsignedInt(String.valueOf(ioStat2).getBytes(STATUS_CHARSET)[0]);
            return ioStat1 + String.format("%03d", twoBytesBinary % 1000);
        }
        return "00" + ioStatus;
    }

    private static boolean isNumeric(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /** COBOL DISPLAY to SYSOUT: the text followed by a newline. */
    private void display(String text) {
        sysout.print(text);
        sysout.print('\n');
    }

    /** Job step entry point (replaces {@code //STEP05 EXEC PGM=CBACT03C} in READXREF.jcl). */
    public static void main(String[] args) {
        String xrefPath = null;
        Charset encoding = Charset.forName("IBM037");
        XrefFile.RecordFormat format = XrefFile.RecordFormat.FIXED;
        for (String arg : args) {
            if (arg.startsWith("--xreffile=")) {
                xrefPath = arg.substring("--xreffile=".length());
            } else if (arg.startsWith("--encoding=")) {
                encoding = Charset.forName(arg.substring("--encoding=".length()));
            } else if (arg.startsWith("--format=")) {
                format = XrefFile.RecordFormat.valueOf(arg.substring("--format=".length()).toUpperCase());
            } else {
                System.err.println("Usage: " + PROGRAM_ID
                        + " --xreffile=<path> [--encoding=IBM037|US-ASCII|...] [--format=FIXED|LINE]");
                System.exit(16);
            }
        }
        if (xrefPath == null) {
            xrefPath = ddName(System.getenv(), "XREFFILE");
        }
        if (xrefPath == null) {
            System.err.println(PROGRAM_ID + ": XREFFILE not allocated (use --xreffile=, DD_XREFFILE or XREFFILE)");
            System.exit(16);
        }

        XrefFile xrefFile = new XrefFile(Path.of(xrefPath), encoding, format);
        try {
            int rc = new Cbact03c(xrefFile, System.out).run();
            System.out.flush();
            System.exit(rc);
        } catch (AbendException e) {
            System.out.flush();
            if (xrefFile.getLastDiagnostic() != null) {
                System.err.println(PROGRAM_ID + ": " + xrefFile.getLastDiagnostic());
            }
            System.err.println(PROGRAM_ID + ": " + e.getMessage());
            // POSIX exit codes are 0-255, so 999 shows up as 231 (999 & 0xFF).
            System.exit(e.getAbendCode());
        }
    }

    static String ddName(Map<String, String> env, String dd) {
        for (String key : new String[] {"DD_" + dd, "dd_" + dd, dd}) {
            String value = env.get(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
