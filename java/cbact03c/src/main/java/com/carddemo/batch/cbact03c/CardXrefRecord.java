package com.carddemo.batch.cbact03c;

import java.util.Objects;

/**
 * Java mapping of copybook {@code app/cpy/CVACT03Y.cpy}: {@code 01 CARD-XREF-RECORD} (RECLN 50).
 *
 * <pre>
 *  Offset Len  COBOL field                         Java accessor
 *  ------ ---  ----------------------------------  -------------------------------
 *       0  16  05 XREF-CARD-NUM   PIC X(16)        {@link #getCardNum()}
 *      16   9  05 XREF-CUST-ID    PIC 9(09)        {@link #getCustId()} / {@link #getCustIdValue()}
 *      25  11  05 XREF-ACCT-ID    PIC 9(11)        {@link #getAcctId()} / {@link #getAcctIdValue()}
 *      36  14  05 FILLER          PIC X(14)        {@link #getFiller()}
 * </pre>
 *
 * <p>The unsigned {@code PIC 9} fields are USAGE DISPLAY (one character per digit), so they are
 * held as their original characters. This keeps the record byte-for-byte identical to the COBOL
 * group item: a COBOL {@code DISPLAY CARD-XREF-RECORD} prints the raw 50 characters even if a
 * numeric field holds non-numeric data, and {@link #toDisplayString()} does the same. Use the
 * {@code ...Value()} accessors when a numeric value is needed.
 */
public final class CardXrefRecord {

    public static final int RECORD_LENGTH = 50;

    static final int CARD_NUM_OFFSET = 0;
    static final int CARD_NUM_LENGTH = 16;
    static final int CUST_ID_OFFSET = 16;
    static final int CUST_ID_LENGTH = 9;
    static final int ACCT_ID_OFFSET = 25;
    static final int ACCT_ID_LENGTH = 11;
    static final int FILLER_OFFSET = 36;
    static final int FILLER_LENGTH = 14;

    private final String cardNum;
    private final String custId;
    private final String acctId;
    private final String filler;

    private CardXrefRecord(String cardNum, String custId, String acctId, String filler) {
        this.cardNum = cardNum;
        this.custId = custId;
        this.acctId = acctId;
        this.filler = filler;
    }

    /** Equivalent of {@code READ XREFFILE-FILE INTO CARD-XREF-RECORD}: a 50-byte group move. */
    public static CardXrefRecord fromRecord(String record) {
        Objects.requireNonNull(record, "record");
        if (record.length() != RECORD_LENGTH) {
            throw new IllegalArgumentException(
                    "CARD-XREF-RECORD must be " + RECORD_LENGTH + " characters, got " + record.length());
        }
        return new CardXrefRecord(
                slice(record, CARD_NUM_OFFSET, CARD_NUM_LENGTH),
                slice(record, CUST_ID_OFFSET, CUST_ID_LENGTH),
                slice(record, ACCT_ID_OFFSET, ACCT_ID_LENGTH),
                slice(record, FILLER_OFFSET, FILLER_LENGTH));
    }

    /**
     * Builds a record the way COBOL MOVEs would: card number left-justified and space-padded,
     * numeric IDs right-justified and zero-padded, FILLER set to spaces.
     */
    public static CardXrefRecord of(String cardNum, long custId, long acctId) {
        Objects.requireNonNull(cardNum, "cardNum");
        if (cardNum.length() > CARD_NUM_LENGTH) {
            throw new IllegalArgumentException("XREF-CARD-NUM is PIC X(16): " + cardNum);
        }
        return new CardXrefRecord(
                padRight(cardNum, CARD_NUM_LENGTH),
                zoned(custId, CUST_ID_LENGTH, "XREF-CUST-ID"),
                zoned(acctId, ACCT_ID_LENGTH, "XREF-ACCT-ID"),
                " ".repeat(FILLER_LENGTH));
    }

    /** XREF-CARD-NUM PIC X(16). */
    public String getCardNum() {
        return cardNum;
    }

    /** XREF-CUST-ID PIC 9(09), as stored (9 characters). */
    public String getCustId() {
        return custId;
    }

    /** XREF-CUST-ID as a number; fails if the field is not numeric. */
    public long getCustIdValue() {
        return parseUnsigned(custId, "XREF-CUST-ID");
    }

    /** XREF-ACCT-ID PIC 9(11), as stored (11 characters). */
    public String getAcctId() {
        return acctId;
    }

    /** XREF-ACCT-ID as a number; fails if the field is not numeric. */
    public long getAcctIdValue() {
        return parseUnsigned(acctId, "XREF-ACCT-ID");
    }

    /** FILLER PIC X(14). */
    public String getFiller() {
        return filler;
    }

    /** The full 50-character record, as {@code DISPLAY CARD-XREF-RECORD} prints it. */
    public String toDisplayString() {
        return cardNum + custId + acctId + filler;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CardXrefRecord && ((CardXrefRecord) o).toDisplayString().equals(toDisplayString());
    }

    @Override
    public int hashCode() {
        return toDisplayString().hashCode();
    }

    @Override
    public String toString() {
        return "CardXrefRecord[cardNum=" + cardNum + ", custId=" + custId + ", acctId=" + acctId + "]";
    }

    private static String slice(String record, int offset, int length) {
        return record.substring(offset, offset + length);
    }

    private static String padRight(String value, int length) {
        return value + " ".repeat(length - value.length());
    }

    private static String zoned(long value, int digits, String field) {
        String text = Long.toString(value);
        if (value < 0 || text.length() > digits) {
            throw new IllegalArgumentException(field + " is PIC 9(" + digits + "): " + value);
        }
        return "0".repeat(digits - text.length()) + text;
    }

    private static long parseUnsigned(String digits, String field) {
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                throw new NumberFormatException(field + " is not numeric: '" + digits + "'");
            }
        }
        return Long.parseLong(digits);
    }
}
