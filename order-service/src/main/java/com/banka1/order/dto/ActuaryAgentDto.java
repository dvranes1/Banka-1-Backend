package com.banka1.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO representing an actuary (agent) with their trading limits and permissions.
 *
 * Combines employee data from employee-service with local ActuaryInfo records to
 * provide a complete view of an agent's trading restrictions and status.
 *
 * Returned by supervisor portal endpoints:
 * <ul>
 *   <li>GET /actuaries - List all agents with limits</li>
 *   <li>GET /actuaries/{id} - Get specific agent details</li>
 * </ul>
 *
 * Note: Supervisors are also employees but do not have daily trading limits
 * (limit is always null for supervisors).
 */
@Data
public class ActuaryAgentDto {

    /** The employee's unique identifier. */
    private Long employeeId;

    /** First name in Serbian (ime). */
    private String ime;

    /** Last name in Serbian (prezime). */
    private String prezime;

    /** Email address for contact and authentication. */
    private String email;

    /** Job position in Serbian (pozicija). Examples: "Analyst", "Manager", "Supervisor" */
    private String pozicija;

    /** Daily trading limit in RSD. Null for supervisors or if not yet configured. */
    private BigDecimal limit;

    /** Amount of the daily limit already consumed in RSD. Resets at 23:59 every day. */
    private BigDecimal usedLimit;

    /**
     * If true, every order placed by this agent requires supervisor approval before execution.
     * Always false for supervisors.
     */
    private Boolean needApproval;
}
