import * as defaultDb from '../db.js';

/** Inserts a new PENDING verification, or replaces the user's open one (one pending per user). */
export async function upsertPending(tx, { userId, idTypeId, idNumber, documentUrl }) {
  const { rows } = await tx.query(
    `SELECT id, document_url FROM identity_verifications WHERE user_id = $1 AND status = 'PENDING' FOR UPDATE`,
    [userId],
  );
  if (rows[0]) {
    await tx.query(
      `UPDATE identity_verifications SET id_type_id = $2, id_number = $3, document_url = $4, submitted_at = now()
       WHERE id = $1`,
      [rows[0].id, idTypeId, idNumber, documentUrl],
    );
    return { id: rows[0].id, previousUrl: rows[0].document_url };
  }
  const inserted = await tx.query(
    `INSERT INTO identity_verifications (user_id, id_type_id, id_number, document_url)
     VALUES ($1, $2, $3, $4) RETURNING id`,
    [userId, idTypeId, idNumber, documentUrl],
  );
  return { id: inserted.rows[0].id, previousUrl: null };
}

export async function listPending(db = defaultDb) {
  const { rows } = await db.query(
    `SELECT v.id, v.user_id, up.full_name, u.email, it.name AS id_type, v.id_number,
            up.address, up.contact_no, v.document_url, v.status, v.submitted_at
     FROM identity_verifications v
     JOIN users u ON u.id = v.user_id
     JOIN id_types it ON it.id_type_id = v.id_type_id
     LEFT JOIN user_profiles up ON up.user_id = v.user_id
     WHERE v.status = 'PENDING'
     ORDER BY v.submitted_at DESC`,
  );
  return rows;
}

export async function lockPending(tx, id) {
  const { rows } = await tx.query(
    `SELECT v.id, v.user_id, up.full_name
     FROM identity_verifications v LEFT JOIN user_profiles up ON up.user_id = v.user_id
     WHERE v.id = $1 AND v.status = 'PENDING'
     FOR UPDATE OF v`,
    [id],
  );
  return rows[0] ?? null;
}

export async function review(tx, { id, status, reviewerId, rejectionReason }) {
  await tx.query(
    `UPDATE identity_verifications
     SET status = $2, reviewed_by = $3, reviewed_at = now(), rejection_reason = $4
     WHERE id = $1`,
    [id, status, reviewerId, rejectionReason ?? null],
  );
}

export async function countPending(db = defaultDb) {
  const { rows } = await db.query(`SELECT COUNT(*)::int AS n FROM identity_verifications WHERE status = 'PENDING'`);
  return rows[0].n;
}
