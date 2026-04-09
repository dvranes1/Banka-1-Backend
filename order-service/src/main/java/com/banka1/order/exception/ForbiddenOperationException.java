package com.banka1.order.exception;

/**
 * Exception thrown when a user attempts an operation they don't have permission for.
 *
 * Causes:
 * <ul>
 *   <li>User attempts to access/modify another user's order</li>
 *   <li>User attempts to access/modify another user's portfolio</li>
 *   <li>User lacks required role for an operation (e.g., AGENT role for option exercise)</li>
 *   <li>User attempts to approve/decline orders (SUPERVISOR role required)</li>
 *   <li>User attempts margin trading without permission</li>
 *   <li>Client attempts operations restricted to AGENT/SUPERVISOR roles</li>
 * </ul>
 *
 * HTTP Response: 403 Forbidden
 */
public class ForbiddenOperationException extends RuntimeException {

    /**
     * Creates a ForbiddenOperationException with the given error message.
     *
     * @param message description of why the operation is forbidden
     */
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
