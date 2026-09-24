package edu.cit.lobitana.supplier;

/**
 * Anything LegacySupply throws at us, wrapped in our own type so its exception classes
 * never escape the module. {@code retryable} decides whether the order stays PENDING
 * (worth another attempt) or is marked FAILED (a permanent refusal).
 */
class LegacySupplyException extends RuntimeException {

    private final boolean retryable;

    LegacySupplyException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    LegacySupplyException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    boolean isRetryable() {
        return retryable;
    }
}
