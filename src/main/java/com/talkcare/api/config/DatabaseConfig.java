package com.talkcare.api.config;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Connection pool for Neon (or any PostgreSQL). {@code DATABASE_URL} is the pooled endpoint used
 * by the API; Flyway uses {@code DATABASE_URL_UNPOOLED} when set because migrations take a
 * session-level advisory lock that a transaction-mode pooler cannot hold.
 */
@Configuration(proxyBeanMethods = false)
public class DatabaseConfig {

    @Bean
    DataSource dataSource(TalkCareProperties properties) {
        DatabaseUrl url = DatabaseUrl.parse(properties.database().url());
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("talkcare");
        dataSource.setJdbcUrl(url.jdbcUrl());
        dataSource.setUsername(url.username());
        dataSource.setPassword(url.password());
        dataSource.setMaximumPoolSize(properties.database().poolMax());
        // Let idle connections close so a Neon compute can scale to zero.
        dataSource.setMinimumIdle(0);
        dataSource.setIdleTimeout(Duration.ofSeconds(10).toMillis());
        dataSource.setMaxLifetime(Duration.ofMinutes(5).toMillis());
        dataSource.setConnectionTimeout(Duration.ofSeconds(15).toMillis());
        return dataSource;
    }

    @Bean
    FlywayConfigurationCustomizer unpooledMigrationConnection(TalkCareProperties properties) {
        return configuration -> {
            String unpooled = properties.database().unpooledUrl();
            if (StringUtils.hasText(unpooled)) {
                DatabaseUrl url = DatabaseUrl.parse(unpooled);
                configuration.dataSource(url.jdbcUrl(), url.username(), url.password());
            }
        };
    }
}
