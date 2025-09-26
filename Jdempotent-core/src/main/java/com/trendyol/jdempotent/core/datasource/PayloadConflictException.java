package com.trendyol.jdempotent.core.datasource;

/**
 * Exception thrown when an idempotency key exists but the request payload differs
 * from the originally stored payload, indicating a potential conflict.
 */
public class PayloadConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new PayloadConflictException with a default message.
     */
    public PayloadConflictException() {
        super("Request payload conflicts with the stored payload for the same idempotency key");
    }

    /**
     * Constructs a new PayloadConflictException with the specified detail message.
     *
     * @param message the detail message
     */
    public PayloadConflictException(String message) {
        super(message);
    }

    /**
     * Constructs a new PayloadConflictException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public PayloadConflictException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new PayloadConflictException with the specified cause.
     *
     * @param cause the cause
     */
    public PayloadConflictException(Throwable cause) {
        super("Request payload conflicts with the stored payload for the same idempotency key", cause);
    }
}
