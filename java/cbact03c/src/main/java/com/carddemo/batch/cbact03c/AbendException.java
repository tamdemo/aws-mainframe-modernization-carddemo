package com.carddemo.batch.cbact03c;

/**
 * Raised where the COBOL program calls Language Environment {@code CEE3ABD} to abend.
 * {@link Cbact03c#main(String[])} turns it into a non-zero process exit.
 */
public final class AbendException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int abendCode;
    private final int timing;

    public AbendException(int abendCode, int timing) {
        super("CEE3ABD abend U" + String.format("%04d", abendCode) + " (timing " + timing + ")");
        this.abendCode = abendCode;
        this.timing = timing;
    }

    /** ABCODE passed to CEE3ABD (999 in CBACT03C). */
    public int getAbendCode() {
        return abendCode;
    }

    /** TIMING passed to CEE3ABD (0 = abend immediately, no LE dump). */
    public int getTiming() {
        return timing;
    }
}
