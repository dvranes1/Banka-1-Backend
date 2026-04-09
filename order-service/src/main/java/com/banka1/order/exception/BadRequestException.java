package com.banka1.order.exception;

/**
 * Exception thrown when a request contains invalid data or violates business rules.
 *
 * Causes:
 * <ul>
 *   <li>Invalid order parameters (negative quantity, missing required fields)</li>
 *   <li>Insufficient account balance for buy orders</li>
 *   <li>Insufficient portfolio quantity for sell orders</li>
 *   <li>Invalid listing ID or non-tradable security</li>
 *   <li>Validation failures (constraints not met)</li>
 * </ul>
 *
 * HTTP Response: 400 Bad Request
 */
public class BadRequestException extends RuntimeException {

    /**
     * Creates a BadRequestException with the given error message.
     *
     * @param message detailed description of what is invalid
     */
    public BadRequestException(String message) {
        super(message);
    }
}
