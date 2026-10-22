package com.carddemo.batch.cbact03c;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Stand-in for the COBOL file definition:
 *
 * <pre>
 *     SELECT XREFFILE-FILE ASSIGN TO   XREFFILE
 *            ORGANIZATION IS INDEXED
 *            ACCESS MODE  IS SEQUENTIAL
 *            RECORD KEY   IS FD-XREF-CARD-NUM
 *            FILE STATUS  IS XREFFILE-STATUS.
 *     FD  XREFFILE-FILE.
 *     01  FD-XREFFILE-REC.
 *         05 FD-XREF-CARD-NUM                  PIC X(16).
 *         05 FD-XREF-DATA                      PIC X(34).
 * </pre>
 *
 * <p>The input is a sequential unload of the CARDXREF KSDS (for example IDCAMS REPRO output, or
 * the sample {@code app/data/EBCDIC/AWS.M2.CARDDEMO.CARDXREF.PS}). A sequential read of a KSDS
 * returns records in ascending key order with unique keys, so the reader checks that
 * FD-XREF-CARD-NUM ascends. If it doesn't, the read fails with status 30 and the program abends,
 * instead of quietly printing records in a different order from the COBOL.
 *
 * <p>Each operation returns the two-character COBOL FILE STATUS it would set:
 * <ul>
 *   <li>00 success, 10 end of file</li>
 *   <li>04 record length does not match the 50-byte fixed record</li>
 *   <li>30 permanent I/O error (including key out of sequence)</li>
 *   <li>35 file not found, 37 file cannot be opened (permission)</li>
 *   <li>41 already open, 42 close when not open, 47 read when not open</li>
 * </ul>
 */
public final class XrefFile {

    /** How records are laid out in the unload file. */
    public enum RecordFormat {
        /** Fixed-length 50-byte records with no delimiters (RECFM=FB, LRECL=50 – mainframe binary transfer). */
        FIXED,
        /** One record per line; short lines are space-padded to 50 bytes (GnuCOBOL LINE SEQUENTIAL rules). */
        LINE
    }

    public static final String STATUS_OK = "00";
    public static final String STATUS_LENGTH_MISMATCH = "04";
    public static final String STATUS_EOF = "10";
    public static final String STATUS_PERMANENT_ERROR = "30";
    public static final String STATUS_NOT_FOUND = "35";
    public static final String STATUS_OPEN_DENIED = "37";
    public static final String STATUS_ALREADY_OPEN = "41";
    public static final String STATUS_NOT_OPEN_FOR_CLOSE = "42";
    public static final String STATUS_NOT_OPEN_FOR_READ = "47";

    private static final int KEY_LENGTH = 16;

    private final Path path;
    private final Charset charset;
    private final RecordFormat format;

    private InputStream in;
    private byte[] previousKey;
    private String record;
    private String lastDiagnostic;

    public XrefFile(Path path, Charset charset, RecordFormat format) {
        this.path = path;
        this.charset = charset;
        this.format = format;
    }

    /** OPEN INPUT XREFFILE-FILE. */
    public String open() {
        if (in != null) {
            return STATUS_ALREADY_OPEN;
        }
        try {
            if (Files.isDirectory(path)) {
                return diagnose(STATUS_OPEN_DENIED, path + " is a directory");
            }
            in = new BufferedInputStream(Files.newInputStream(path));
            previousKey = null;
            record = null;
            return STATUS_OK;
        } catch (NoSuchFileException e) {
            return diagnose(STATUS_NOT_FOUND, "file not found: " + path);
        } catch (AccessDeniedException e) {
            return diagnose(STATUS_OPEN_DENIED, "access denied: " + path);
        } catch (IOException e) {
            return diagnose(STATUS_PERMANENT_ERROR, e.toString());
        }
    }

    /** READ XREFFILE-FILE (next record). On status 00, {@link #getRecord()} holds FD-XREFFILE-REC. */
    public String read() {
        if (in == null) {
            return STATUS_NOT_OPEN_FOR_READ;
        }
        try {
            byte[] raw = format == RecordFormat.FIXED ? readFixed() : readLine();
            if (raw == null) {
                return STATUS_EOF;
            }
            if (raw.length != CardXrefRecord.RECORD_LENGTH) {
                return diagnose(STATUS_LENGTH_MISMATCH,
                        "record is " + raw.length + " bytes, expected " + CardXrefRecord.RECORD_LENGTH);
            }
            byte[] key = Arrays.copyOf(raw, KEY_LENGTH);
            if (previousKey != null && Arrays.compareUnsigned(key, previousKey) <= 0) {
                return diagnose(STATUS_PERMANENT_ERROR, "key out of sequence or duplicate: '"
                        + new String(key, charset) + "' after '" + new String(previousKey, charset) + "'");
            }
            String decoded = new String(raw, charset);
            if (decoded.length() != CardXrefRecord.RECORD_LENGTH) {
                return diagnose(STATUS_LENGTH_MISMATCH, "record decodes to " + decoded.length()
                        + " characters in " + charset + ", expected " + CardXrefRecord.RECORD_LENGTH);
            }
            previousKey = key;
            record = decoded;
            return STATUS_OK;
        } catch (IOException e) {
            return diagnose(STATUS_PERMANENT_ERROR, e.toString());
        }
    }

    /** CLOSE XREFFILE-FILE. */
    public String close() {
        if (in == null) {
            return STATUS_NOT_OPEN_FOR_CLOSE;
        }
        try {
            in.close();
            return STATUS_OK;
        } catch (IOException e) {
            return diagnose(STATUS_PERMANENT_ERROR, e.toString());
        } finally {
            in = null;
        }
    }

    /** FD-XREFFILE-REC after the last successful read. */
    public String getRecord() {
        return record;
    }

    /** Why the last non-00 status was set, for operator logs (not part of the COBOL SYSOUT). */
    public String getLastDiagnostic() {
        return lastDiagnostic;
    }

    private byte[] readFixed() throws IOException {
        byte[] buf = in.readNBytes(CardXrefRecord.RECORD_LENGTH);
        return buf.length == 0 ? null : buf;
    }

    private byte[] readLine() throws IOException {
        java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream(CardXrefRecord.RECORD_LENGTH);
        int b = in.read();
        if (b == -1) {
            return null;
        }
        while (b != -1 && b != '\n') {
            line.write(b);
            b = in.read();
        }
        byte[] bytes = line.toByteArray();
        int len = bytes.length;
        if (len > 0 && bytes[len - 1] == '\r') {
            len--;
        }
        if (len > CardXrefRecord.RECORD_LENGTH) {
            return Arrays.copyOf(bytes, len);
        }
        byte[] padded = Arrays.copyOf(bytes, CardXrefRecord.RECORD_LENGTH);
        Arrays.fill(padded, len, padded.length, " ".getBytes(charset)[0]);
        return padded;
    }

    private String diagnose(String status, String message) {
        lastDiagnostic = message;
        return status;
    }
}
