package com.trendyol.jdempotent.postgres;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fixed rate scheduler for Jdempotent PostgreSQL cleanup operations.
 * 
 * <p>This scheduler is only created when {@code jdempotent.postgres.scheduler.fixedRate} 
 * is configured, ensuring only one scheduling mechanism is active.</p>
 */
@Component
public class JdempotentFixedRateScheduler {

    private final JdempotentPostgresCleanupService cleanupService;

    public JdempotentFixedRateScheduler(
            JdempotentPostgresCleanupService cleanupService,
            JdempotentPostgresProperties postgresProperties) {
        this.cleanupService = cleanupService;
    }

    /**
     * Executes cleanup at fixed rate intervals.
     */
    @Scheduled(
        fixedRateString = "${jdempotent.postgres.scheduler.fixedRate}",
        initialDelayString = "${jdempotent.postgres.scheduler.initialDelay:60000}"
    )
    public void executeCleanup() {
        cleanupService.executeScheduledCleanup();
    }
}
