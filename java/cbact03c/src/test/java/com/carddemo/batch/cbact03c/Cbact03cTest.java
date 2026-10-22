package com.carddemo.batch.cbact03c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class Cbact03cTest {

    private static final Charset EBCDIC = Charset.forName("IBM037");
    private static final Path SAMPLE_EBCDIC = Path.of("../../app/data/EBCDIC/AWS.M2.CARDDEMO.CARDXREF.PS");
    private static final Path SAMPLE_ASCII = Path.of("../../app/data/ASCII/cardxref.txt");

    /** SYSOUT of the original CBACT03C (GnuCOBOL 3.1) reading the sample KSDS – see parity/. */
    private static final Path GOLDEN_SAMPLE = Path.of("src/test/resources/golden/CBACT03C-sample-sysout.txt");
    private static final Path GOLDEN_OPEN_ERROR = Path.of("src/test/resources/golden/CBACT03C-open-error-sysout.txt");

    private static final String REC1 = "050002445376574000000005000000000050              ";
    private static final String REC2 = "068358619817151600000002700000000027              ";

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    @Test
    void sampleEbcdicFileMatchesCobolSysoutLineByLine() throws IOException {
        int rc = run(new XrefFile(SAMPLE_EBCDIC, EBCDIC, XrefFile.RecordFormat.FIXED));

        assertEquals(0, rc);
        assertEquals(Files.readString(GOLDEN_SAMPLE), sysout());
    }

    @Test
    void sampleAsciiLineFileMatchesCobolSysoutLineByLine() throws IOException {
        int rc = run(new XrefFile(SAMPLE_ASCII, StandardCharsets.US_ASCII, XrefFile.RecordFormat.LINE));

        assertEquals(0, rc);
        assertEquals(Files.readString(GOLDEN_SAMPLE), sysout());
    }

    @Test
    void printsEachRecordTwiceLikeTheCobol() throws IOException {
        Path file = fixedFile(REC1 + REC2);

        run(new XrefFile(file, EBCDIC, XrefFile.RecordFormat.FIXED));

        assertEquals(List.of(
                "START OF EXECUTION OF PROGRAM CBACT03C",
                REC1, REC1,
                REC2, REC2,
                "END OF EXECUTION OF PROGRAM CBACT03C"), lines());
    }

    @Test
    void emptyFilePrintsOnlyBanners() throws IOException {
        run(new XrefFile(fixedFile(""), EBCDIC, XrefFile.RecordFormat.FIXED));

        assertEquals(List.of(
                "START OF EXECUTION OF PROGRAM CBACT03C",
                "END OF EXECUTION OF PROGRAM CBACT03C"), lines());
    }

    @Test
    void missingFileAbendsLikeTheCobol() throws IOException {
        XrefFile file = new XrefFile(Path.of("does-not-exist.ps"), EBCDIC, XrefFile.RecordFormat.FIXED);

        AbendException abend = assertThrows(AbendException.class, () -> run(file));

        assertEquals(999, abend.getAbendCode());
        assertEquals(0, abend.getTiming());
        assertEquals(Files.readString(GOLDEN_OPEN_ERROR), sysout());
    }

    @Test
    void shortRecordIsAReadErrorWithStatus04() throws IOException {
        Path file = fixedFile(REC1 + REC2.substring(0, 20));

        assertThrows(AbendException.class, () -> run(new XrefFile(file, EBCDIC, XrefFile.RecordFormat.FIXED)));

        assertEquals(List.of(
                "START OF EXECUTION OF PROGRAM CBACT03C",
                REC1, REC1,
                "ERROR READING XREFFILE",
                "FILE STATUS IS: NNNN0004",
                "ABENDING PROGRAM"), lines());
    }

    @Test
    void keyOutOfSequenceIsAReadErrorWithStatus30() throws IOException {
        Path file = fixedFile(REC2 + REC1);

        assertThrows(AbendException.class, () -> run(new XrefFile(file, EBCDIC, XrefFile.RecordFormat.FIXED)));

        assertEquals(List.of(
                "START OF EXECUTION OF PROGRAM CBACT03C",
                REC2, REC2,
                "ERROR READING XREFFILE",
                "FILE STATUS IS: NNNN0030",
                "ABENDING PROGRAM"), lines());
    }

    @Test
    void ioStatusFormattingFollows9910Paragraph() {
        assertEquals("0035", Cbact03c.formatIoStatus("35"));
        assertEquals("0004", Cbact03c.formatIoStatus("04"));
        // '9x' statuses: IO-STAT1 then the EBCDIC code point of IO-STAT2 ('A' = X'C1' = 193).
        assertEquals("9193", Cbact03c.formatIoStatus("9A"));
        assertEquals("9241", Cbact03c.formatIoStatus("91"));
        // Non-numeric status takes the same branch.
        assertEquals("3193", Cbact03c.formatIoStatus("3A"));
    }

    @Test
    void ddNameLookupMatchesGnuCobolOrder() {
        assertEquals("a", Cbact03c.ddName(Map.of("DD_XREFFILE", "a", "XREFFILE", "c"), "XREFFILE"));
        assertEquals("c", Cbact03c.ddName(Map.of("XREFFILE", "c"), "XREFFILE"));
        assertEquals(null, Cbact03c.ddName(Map.of(), "XREFFILE"));
    }

    private int run(XrefFile file) {
        return new Cbact03c(file, new PrintStream(out, true, StandardCharsets.UTF_8)).run();
    }

    private String sysout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private List<String> lines() {
        return List.of(sysout().split("\n", -1)).subList(0, (int) sysout().chars().filter(c -> c == '\n').count());
    }

    private static Path fixedFile(String records) throws IOException {
        Path file = Files.createTempFile("cardxref", ".ps");
        file.toFile().deleteOnExit();
        Files.write(file, records.getBytes(EBCDIC));
        return file;
    }
}
