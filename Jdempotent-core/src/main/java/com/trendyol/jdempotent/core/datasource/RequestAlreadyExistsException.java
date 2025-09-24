package com.trendyol.jdempotent.core.datasource;

/**
 * Exception thrown when attempting to store a request that already exists in the idempotent repository.
 * This typically occurs when a duplicate request is made with the same idempotency key.
 */
public class RequestAlreadyExistsException extends Exception {

    /**
     * Constructs a new RequestAlreadyExistsException with no detail message.
     */
    public RequestAlreadyExistsException() {
        super();
    }

    /**
     * Constructs a new RequestAlreadyExistsException with the specified detail message.
     *
     * @param message the detail message
     */
    public RequestAlreadyExistsException(String message) {
        super(message);
    }

    /**
     * Constructs a new RequestAlreadyExistsException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public RequestAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new RequestAlreadyExistsException with the specified cause.
     *
     * @param cause the cause of the exception
     */
    public RequestAlreadyExistsException(Throwable cause) {
        super(cause);
    }
}
