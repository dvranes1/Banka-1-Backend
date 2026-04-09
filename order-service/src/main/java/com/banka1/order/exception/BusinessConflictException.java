package com.banka1.order.exception;

/**
 * Exception thrown when an operation conflicts with the current business state or rules.
 *
 * Causes:
 * <ul>
 *   <li>Attempting to execute an order in invalid state (not APPROVED or PENDING_EXECUTION)</li>
 *   <li>Exceeding daily trading limit for agents</li>
 *   <li>Attempting to approve/decline non-pending orders</li>
 *   <li>Portfolio quantity changes conflicting with existing reservations</li>
 *   <li>Exchange is closed when order requires execution</li>
 *   <li>Tax already charged for a transaction</li>
 * </ul>
 *
 * HTTP Response: 409 Conflict
 */
public class BusinessConflictException extends RuntimeException {

    /**
     * Creates a BusinessConflictException with the given error message.
     *
     * @param message description of what business rule conflict occurred
     */
    public BusinessConflictException(String message) {
        super(message);
    }
}
