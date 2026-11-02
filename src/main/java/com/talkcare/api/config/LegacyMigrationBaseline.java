package com.talkcare.api.config;

import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Takes over a database that the Express version migrated. That runner recorded applied files
 * ({@code 001_schema.sql} ...) in {@code schema_migrations}; Flyway keeps its own history table.
 * On the first Flyway run against such a database, the history is baselined at the highest
 * legacy version, so V1..Vn are not re-applied and only newer migrations run.
 */
@Component
public class LegacyMigrationBaseline implements FlywayMigrationStrategy {

    private static final Logger log = LoggerFactory.getLogger(LegacyMigrationBaseline.class);
    private static final Pattern LEGACY_VERSION = Pattern.compile("^(\\d+)_.*\\.sql$");

    @Override
    public void migrate(Flyway flyway) {
        if (flyway.info().applied().length == 0) {
            legacyVersion(new JdbcTemplate(flyway.getConfiguration().getDataSource())).ifPresent(version -> {
                log.info("Found migrations applied by the Express runner; baselining Flyway at version {}.", version);
                Flyway.configure()
                        .configuration(flyway.getConfiguration())
                        .baselineVersion(MigrationVersion.fromVersion(Integer.toString(version)))
                        .baselineDescription("Applied by the Express migration runner")
                        .load()
                        .baseline();
            });
        }
        flyway.migrate();
    }

    static OptionalInt legacyVersion(JdbcTemplate jdbc) {
        Boolean exists = jdbc.queryForObject("SELECT to_regclass('schema_migrations') IS NOT NULL", Boolean.class);
        if (!Boolean.TRUE.equals(exists)) {
            return OptionalInt.empty();
        }
        List<String> files = jdbc.queryForList("SELECT version FROM schema_migrations", String.class);
        return files.stream()
                .map(LEGACY_VERSION::matcher)
                .filter(Matcher::matches)
                .mapToInt(m -> Integer.parseInt(m.group(1)))
                .max();
    }
}
