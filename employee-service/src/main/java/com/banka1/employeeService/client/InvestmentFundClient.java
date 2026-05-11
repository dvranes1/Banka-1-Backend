package com.banka1.employeeService.client;

/**
 * Client adapter for investment-fund-service management-transfer operations.
 */
public interface InvestmentFundClient {

    /**
     * Reassigns all funds managed by one employee to another employee.
     *
     * @param fromUserId previous fund manager
     * @param toUserId new fund manager
     * @param bearerToken JWT of the authenticated admin performing the action
     */
    void transferManagement(Long fromUserId, Long toUserId, String bearerToken);
}
