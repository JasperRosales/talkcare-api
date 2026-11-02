import * as defaultDb from '../db.js';

export async function log(db, { actorId, entityType, entityId, action, details }) {
  await db.query(
    `INSERT INTO activity_log (actor_user_id, entity_type, entity_id, action, details) VALUES ($1, $2, $3, $4, $5)`,
    [actorId ?? null, entityType, entityId, action, details ?? null],
  );
}

export async function recent(limit = 12, db = defaultDb) {
  const { rows } = await db.query(
    `SELECT a.action, a.details, a.entity_type, a.entity_id, a.created_at, up.full_name AS actor_name
     FROM activity_log a LEFT JOIN user_profiles up ON up.user_id = a.actor_user_id
     ORDER BY a.created_at DESC, a.id DESC LIMIT $1`,
    [limit],
  );
  return rows;
}

export async function notify(db, { userId, message, type = 'SYSTEM' }) {
  await db.query('INSERT INTO notifications (user_id, type, message) VALUES ($1, $2, $3)', [userId, type, message]);
}

export async function notificationsFor(userId, db = defaultDb) {
  const { rows } = await db.query(
    `SELECT id, type, message, is_read, created_at, read_at FROM notifications
     WHERE user_id = $1 ORDER BY created_at DESC LIMIT 50`,
    [userId],
  );
  return rows;
}

export async function markNotificationsRead(userId, db = defaultDb) {
  await db.query(
    'UPDATE notifications SET is_read = TRUE, read_at = now() WHERE user_id = $1 AND is_read = FALSE',
    [userId],
  );
}
