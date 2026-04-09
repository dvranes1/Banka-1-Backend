package com.banka1.order.exception;

/**
 * Exception thrown when a requested entity is not found.
 *
 * Causes:
 * <ul>
 *   <li>Order with given ID does not exist</li>
 *   <li>Portfolio position with given ID does not exist</li>
 *   <li>Actuary info for given employee ID does not exist</li>
 *   <li>Tax charge record for given ID does not exist</li>
 *   <li>Security listing not found in stock-service</li>
 * </ul>
 *
 * HTTP Response: 404 Not Found
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Creates a ResourceNotFoundException with the given error message.
     *
     * @param message description of what entity was not found
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
