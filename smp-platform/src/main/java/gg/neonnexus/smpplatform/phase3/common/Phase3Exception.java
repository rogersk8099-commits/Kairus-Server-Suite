package gg.neonnexus.smpplatform.phase3.common;

/** A stable, command-safe business failure. Persistence outages must surface as FAILED_STORAGE. */
public final class Phase3Exception extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Code {
        FORBIDDEN, NOT_FOUND, ALREADY_EXISTS, INVALID_ARGUMENT, POLICY_DENIED,
        INSUFFICIENT_BALANCE, CONCURRENT_MODIFICATION, FAILED_STORAGE
    }

    private final Code code;

    public Phase3Exception(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Phase3Exception(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() { return code; }
}
