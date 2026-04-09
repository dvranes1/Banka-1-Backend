package com.banka1.order.dto;

import java.util.Set;

/**
 * Minimal authenticated-user context extracted from the incoming JWT token.
 *
 * This record encapsulates the essential user information needed for authorization
 * and business logic checks. Created from JWT claims and passed through the service
 * layer for permission validation and audit logging.
 *
 * JWT Claims Mapping:
 * <ul>
 *   <li>userId: JWT subject claim (sub) - uniquely identifies the user</li>
 *   <li>roles: JWT roles claim - list of role strings (e.g., "CLIENT_TRADING", "AGENT", "SUPERVISOR")</li>
 *   <li>permissions: JWT permissions claim - list of specific permission strings</li>
 * </ul>
 *
 * Example JWT Claims:
 * <pre>
 * {
 *   "sub": "12345",
 *   "roles": ["CLIENT_TRADING"],
 *   "permissions": ["trading:execute", "margin:use"]
 * }
 * </pre>
 */
public record AuthenticatedUser(Long userId, Set<String> roles, Set<String> permissions) {

    /**
     * Checks if the user has a specific role (case-insensitive).
     *
     * @param role the role name to check (e.g., "CLIENT_TRADING", "AGENT")
     * @return true if the user has this role
     */
    public boolean hasRole(String role) {
        return roles.stream().anyMatch(current -> current.equalsIgnoreCase(role));
    }

    /**
     * Checks if the user has permission to use margin (borrowed funds).
     *
     * @return true if user has margin permission
     */
    public boolean hasMarginPermission() {
        return permissions.stream().anyMatch(permission -> permission.toLowerCase().contains("margin"));
    }

    /**
     * Checks if the user has trading permissions.
     *
     * Users with CLIENT_TRADING role or explicit trading permission can trade.
     *
     * @return true if user can place orders
     */
    public boolean hasTradingPermission() {
        return hasRole("CLIENT_TRADING")
                || permissions.stream().anyMatch(permission -> permission.toLowerCase().contains("trading"));
    }

    /**
     * Checks if the user is a client (not an employee/agent).
     *
     * @return true if user has any CLIENT role
     */
    public boolean isClient() {
        return hasRole("CLIENT_BASIC") || hasRole("CLIENT_TRADING") || hasRole("CLIENT");
    }

    /**
     * Checks if the user is an agent (employee with trading authority).
     *
     * @return true if user has AGENT, SUPERVISOR, or ADMIN role
     */
    public boolean isAgent() {
        return hasRole("AGENT") || hasRole("SUPERVISOR") || hasRole("ADMIN");
    }
}
