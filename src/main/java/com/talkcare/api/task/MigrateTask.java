package com.talkcare.api.task;

import org.springframework.stereotype.Component;

/**
 * Release step. Flyway runs pending migrations while the application context starts (see
 * LegacyMigrationBaseline), so by the time this task runs there is nothing left to do.
 */
@Component
class MigrateTask implements CliTask {

    @Override
    public String name() {
        return "migrate";
    }

    @Override
    public void run() {
        // Migrations were applied during startup.
    }
}
