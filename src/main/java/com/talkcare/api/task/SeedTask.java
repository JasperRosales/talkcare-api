package com.talkcare.api.task;

import com.talkcare.api.config.TalkCareProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates one test account per role (docs/schema.md §5). Idempotent: accounts whose username or
 * email already exists are skipped. Refuses to run in production unless ALLOW_SEED=true.
 */
@Component
class SeedTask implements CliTask {

    private static final Logger log = LoggerFactory.getLogger(SeedTask.class);

    private final JdbcClient jdbc;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transaction;
    private final TalkCareProperties properties;

    SeedTask(JdbcClient jdbc, PasswordEncoder passwords, TransactionTemplate transaction, TalkCareProperties properties) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.transaction = transaction;
        this.properties = properties;
    }

    /** A test account; subclasses insert the role's subtype rows. */
    private abstract static class SeedAccount {
        final String role;
        final String username;
        final String password;
        final String fullName;

        SeedAccount(String role, String username, String password, String fullName) {
            this.role = role;
            this.username = username;
            this.password = password;
            this.fullName = fullName;
        }

        String email() {
            return username + "@talkcare.com";
        }

        abstract void insertSubtype(JdbcClient jdbc, int userId);
    }

    @Override
    public String name() {
        return "seed";
    }

    @Override
    public void run() {
        if (properties.isProduction() && !properties.seed().allowInProduction()) {
            throw new IllegalStateException("Refusing to seed test accounts in production. Set ALLOW_SEED=true to override.");
        }
        for (SeedAccount account : accounts()) {
            Boolean created = transaction.execute(status -> create(account));
            log.info("{} {} {}", Boolean.TRUE.equals(created) ? "Created" : "Skipped (exists)",
                    String.format("%-12s", account.role), account.username);
        }
    }

    private boolean create(SeedAccount account) {
        boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM users WHERE LOWER(username) = LOWER(:u) OR LOWER(email) = LOWER(:e))")
                .param("u", account.username)
                .param("e", account.email())
                .query(Boolean.class)
                .single();
        if (exists) {
            return false;
        }
        int id = jdbc.sql("""
                        INSERT INTO users (role_id, username, email, password_hash, status)
                        SELECT role_id, :username, :email, :hash, 'VERIFIED' FROM roles WHERE code = :role RETURNING id
                        """)
                .param("role", account.role)
                .param("username", account.username)
                .param("email", account.email())
                .param("hash", passwords.encode(account.password))
                .query(Integer.class)
                .single();
        jdbc.sql("INSERT INTO user_profiles (user_id, full_name) VALUES (:id, :name)")
                .param("id", id)
                .param("name", account.fullName)
                .update();
        account.insertSubtype(jdbc, id);
        return true;
    }

    private List<SeedAccount> accounts() {
        TalkCareProperties.Seed seed = properties.seed();
        return List.of(
                new SeedAccount("CLIENT", "test.user", seed.clientPassword(), "TalkCare Test User") {
                    @Override
                    void insertSubtype(JdbcClient jdbc, int userId) {
                        jdbc.sql("INSERT INTO clients (user_id, consent_to_terms_at) VALUES (:id, now())").param("id", userId).update();
                    }
                },
                new SeedAccount("ATTORNEY", "test.attorney", seed.attorneyPassword(), "Atty. Sofia Ramos") {
                    @Override
                    void insertSubtype(JdbcClient jdbc, int userId) {
                        jdbc.sql("""
                                INSERT INTO professionals (user_id, professional_type, display_title, bio, years_of_experience)
                                VALUES (:id, 'ATTORNEY', 'Atty.', 'JusticeCare legal counsel', 8)
                                """).param("id", userId).update();
                        jdbc.sql("""
                                INSERT INTO attorneys (user_id, bar_roll_no, ibp_no, law_firm, jurisdiction)
                                VALUES (:id, 'BAR-0001', 'IBP-0001', 'JusticeCare Center', 'Metro Manila')
                                """).param("id", userId).update();
                    }
                },
                new SeedAccount("PSYCHIATRIST", "test.psychiatrist", seed.psychiatristPassword(), "Dr. Eleanor Reyes") {
                    @Override
                    void insertSubtype(JdbcClient jdbc, int userId) {
                        jdbc.sql("""
                                INSERT INTO professionals (user_id, professional_type, display_title, bio, years_of_experience)
                                VALUES (:id, 'PSYCHIATRIST', 'Dr.', 'ThoMatt mental health consultant', 10)
                                """).param("id", userId).update();
                        jdbc.sql("""
                                INSERT INTO psychiatrists (user_id, prc_license_no, clinic_name, accepts_medication_management)
                                VALUES (:id, 'PRC-0001', 'ThoMatt Mental Health Clinic', TRUE)
                                """).param("id", userId).update();
                    }
                },
                new SeedAccount("ADMIN", "test.admin", seed.adminPassword(), "TalkCare Test Admin") {
                    @Override
                    void insertSubtype(JdbcClient jdbc, int userId) {
                        jdbc.sql("""
                                INSERT INTO admins (user_id, job_title, department, is_super_admin)
                                VALUES (:id, 'System Administrator', 'Operations', TRUE)
                                """).param("id", userId).update();
                    }
                });
    }
}
