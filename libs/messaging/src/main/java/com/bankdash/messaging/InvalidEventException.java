package com.bankdash.messaging;

/**
 * The record can never be processed, however often it is retried: its payload does not match the event's
 * contract. The error handler sends it straight to the {@code .DLT}.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
