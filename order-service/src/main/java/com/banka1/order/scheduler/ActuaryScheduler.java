package com.banka1.order.scheduler;

import com.banka1.order.service.ActuaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks for actuary limit management.
 *
 * Automatically runs daily maintenance tasks for actuary trading limits.
 * As specified in the Celina 3 actuary specification, the daily trading limit
 * for agents resets every day at 23:59.
 *
 * Scheduled Operations:
 * <ul>
 *   <li>Reset daily used limits for all agents (23:59 daily)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActuaryScheduler {

    private final ActuaryService actuaryService;

    /**
     * Resets the {@code usedLimit} to zero for every agent record.
     * Runs every day at 23:59:00 (cron: "0 59 23 * * *").
     *
     * This allows agents to resume trading with a fresh daily limit at midnight.
     * Supervisors have no daily limit, so they are not affected.
     */
    @Scheduled(cron = "0 59 23 * * *")
    public void resetDailyLimits() {
        log.info("Running daily usedLimit reset for all agents.");
        actuaryService.resetAllLimits();
    }
}
