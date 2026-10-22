package com.carddemo.batch.cbact03c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CardXrefRecordTest {

    // First record of app/data/ASCII/cardxref.txt, padded to LRECL 50.
    private static final String SAMPLE = "050002445376574000000005000000000050              ";

    @Test
    void mapsCvact03yFieldsAtCopybookOffsets() {
        CardXrefRecord rec = CardXrefRecord.fromRecord(SAMPLE);

        assertEquals("0500024453765740", rec.getCardNum());
        assertEquals("000000050", rec.getCustId());
        assertEquals("00000000050", rec.getAcctId());
        assertEquals(" ".repeat(14), rec.getFiller());
        assertEquals(50L, rec.getCustIdValue());
        assertEquals(50L, rec.getAcctIdValue());
    }

    @Test
    void displayStringIsTheRaw50ByteRecord() {
        assertEquals(SAMPLE, CardXrefRecord.fromRecord(SAMPLE).toDisplayString());
    }

    @Test
    void ofPadsLikeCobolMoves() {
        CardXrefRecord rec = CardXrefRecord.of("0500024453765740", 50, 50);
        assertEquals(SAMPLE, rec.toDisplayString());
        assertEquals(CardXrefRecord.fromRecord(SAMPLE), rec);
    }

    @Test
    void nonNumericIdIsKeptAsIsButHasNoNumericValue() {
        String bad = "0500024453765740ABCDEFGHI00000000050              ";
        CardXrefRecord rec = CardXrefRecord.fromRecord(bad);
        assertEquals(bad, rec.toDisplayString());
        assertThrows(NumberFormatException.class, rec::getCustIdValue);
    }

    @Test
    void rejectsWrongLengthAndOverflow() {
        assertThrows(IllegalArgumentException.class, () -> CardXrefRecord.fromRecord(SAMPLE.trim()));
        assertThrows(IllegalArgumentException.class, () -> CardXrefRecord.of("1", 1_000_000_000L, 1));
        assertThrows(IllegalArgumentException.class, () -> CardXrefRecord.of("1", 1, -1));
        assertThrows(IllegalArgumentException.class, () -> CardXrefRecord.of("12345678901234567", 1, 1));
    }
}
