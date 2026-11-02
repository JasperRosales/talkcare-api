import * as defaultDb from '../db.js';

const MESSAGE_SELECT = `
  SELECT m.id, m.conversation_id, m.sender_id, m.message_text, m.is_auto_reply, m.created_at,
         up.full_name AS sender_name, u.username AS sender_username, r.code AS sender_role_code,
         p.display_title AS sender_title,
         COALESCE(
           (SELECT json_agg(json_build_object('url', a.file_url, 'mimeType', a.mime_type, 'byteSize', a.byte_size)
                            ORDER BY a.id)
            FROM message_attachments a WHERE a.message_id = m.id),
           '[]'::json) AS attachments
  FROM messages m
  LEFT JOIN users u          ON u.id = m.sender_id
  LEFT JOIN roles r          ON r.role_id = u.role_id
  LEFT JOIN user_profiles up ON up.user_id = m.sender_id
  LEFT JOIN professionals p  ON p.user_id = m.sender_id`;

export async function listByConversation(conversationId, db = defaultDb) {
  const { rows } = await db.query(`${MESSAGE_SELECT} WHERE m.conversation_id = $1 ORDER BY m.created_at, m.id`, [
    conversationId,
  ]);
  return rows;
}

export async function listByIds(ids, db = defaultDb) {
  const { rows } = await db.query(`${MESSAGE_SELECT} WHERE m.id = ANY($1::int[]) ORDER BY m.created_at, m.id`, [ids]);
  return rows;
}

/** Inserts a message (and optional attachment). Must run inside a transaction. */
export async function create(tx, { conversationId, senderId, text, autoReply = false, attachment = null }) {
  const { rows } = await tx.query(
    `INSERT INTO messages (conversation_id, sender_id, message_text, is_auto_reply)
     VALUES ($1, $2, $3, $4) RETURNING id`,
    [conversationId, autoReply ? null : senderId, text || null, autoReply],
  );
  const id = rows[0].id;
  if (attachment) {
    await tx.query(
      `INSERT INTO message_attachments (message_id, file_url, mime_type, byte_size) VALUES ($1, $2, $3, $4)`,
      [id, attachment.url, attachment.mimeType, attachment.byteSize],
    );
  }
  return id;
}

export async function hasAny(tx, conversationId) {
  const { rows } = await tx.query('SELECT 1 FROM messages WHERE conversation_id = $1 LIMIT 1', [conversationId]);
  return rows.length > 0;
}
