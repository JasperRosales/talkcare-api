export async function replaceForUser(tx, { userId, tokenHash, ttlMinutes }) {
  await tx.query('DELETE FROM password_reset_tokens WHERE user_id = $1', [userId]);
  await tx.query(
    `INSERT INTO password_reset_tokens (token_hash, user_id, expires_at)
     VALUES ($1, $2, now() + make_interval(mins => $3))`,
    [tokenHash, userId, ttlMinutes],
  );
}

export async function lockUsable(tx, tokenHash) {
  const { rows } = await tx.query(
    `SELECT user_id FROM password_reset_tokens
     WHERE token_hash = $1 AND used_at IS NULL AND expires_at > now() FOR UPDATE`,
    [tokenHash],
  );
  return rows[0]?.user_id ?? null;
}

export async function markUsed(tx, tokenHash) {
  await tx.query('UPDATE password_reset_tokens SET used_at = now() WHERE token_hash = $1', [tokenHash]);
}

export async function remove(db, tokenHash) {
  await db.query('DELETE FROM password_reset_tokens WHERE token_hash = $1', [tokenHash]);
}
