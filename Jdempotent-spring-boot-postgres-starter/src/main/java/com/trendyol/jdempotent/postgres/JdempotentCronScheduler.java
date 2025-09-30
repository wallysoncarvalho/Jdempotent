package com.trendyol.jdempotent.postgres;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron scheduler for Jdempotent PostgreSQL cleanup operations.
 * 
 * <p>This scheduler is only created when {@code jdempotent.postgres.scheduler.cron} 
 * is configured, ensuring only one scheduling mechanism is active.</p>
 */
@Component
public class JdempotentCronScheduler {

    private final JdempotentPostgresCleanupService cleanupService;

    public JdempotentCronScheduler(
            JdempotentPostgresCleanupService cleanupService,
            JdempotentPostgresProperties postgresProperties) {
        this.cleanupService = cleanupService;
    }

    /**
     * Executes cleanup based on cron expression.
     */
    @Scheduled(
        cron = "${jdempotent.postgres.scheduler.cron}",
        zone = "${jdempotent.postgres.scheduler.zone:UTC}"
    )
    public void executeCleanup() {
        cleanupService.executeScheduledCleanup();
    }
}
