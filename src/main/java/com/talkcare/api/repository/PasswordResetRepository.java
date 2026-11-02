package com.talkcare.api.repository;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** One-time password reset tokens, stored as SHA-256 hashes. */
@Repository
public class PasswordResetRepository {

    private final JdbcClient jdbc;

    public PasswordResetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Replaces any earlier token of the user. Call inside a transaction. */
    public void replaceForUser(int userId, String tokenHash, int ttlMinutes) {
        jdbc.sql("DELETE FROM password_reset_tokens WHERE user_id = :userId").param("userId", userId).update();
        jdbc.sql("""
                        INSERT INTO password_reset_tokens (token_hash, user_id, expires_at)
                        VALUES (:tokenHash, :userId, now() + make_interval(mins => :ttl))
                        """)
                .param("tokenHash", tokenHash)
                .param("userId", userId)
                .param("ttl", ttlMinutes)
                .update();
    }

    /** User id of an unused, unexpired token, locked for update. */
    public Optional<Integer> lockUsable(String tokenHash) {
        return jdbc.sql("""
                        SELECT user_id FROM password_reset_tokens
                        WHERE token_hash = :tokenHash AND used_at IS NULL AND expires_at > now() FOR UPDATE
                        """)
                .param("tokenHash", tokenHash)
                .query(Integer.class)
                .optional();
    }

    public void markUsed(String tokenHash) {
        jdbc.sql("UPDATE password_reset_tokens SET used_at = now() WHERE token_hash = :tokenHash")
                .param("tokenHash", tokenHash)
                .update();
    }

    public void remove(String tokenHash) {
        jdbc.sql("DELETE FROM password_reset_tokens WHERE token_hash = :tokenHash").param("tokenHash", tokenHash).update();
    }
}
