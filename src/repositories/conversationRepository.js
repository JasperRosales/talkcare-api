import * as defaultDb from '../db.js';

const CONVERSATION_SELECT = `
  SELECT c.id, c.client_id, c.professional_id, c.title, c.status, c.created_at, c.updated_at,
         st.code AS service_type,
         cup.full_name AS client_name,
         pup.full_name AS professional_name,
         p.display_title AS professional_title,
         p.professional_type
  FROM conversations c
  JOIN service_types st       ON st.service_type_id = c.service_type_id
  JOIN professionals p        ON p.user_id = c.professional_id
  LEFT JOIN user_profiles cup ON cup.user_id = c.client_id
  LEFT JOIN user_profiles pup ON pup.user_id = c.professional_id`;

export async function findById(id, db = defaultDb) {
  const { rows } = await db.query(`${CONVERSATION_SELECT} WHERE c.id = $1`, [id]);
  return rows[0] ?? null;
}

export async function findOpen(clientId, serviceCode, db = defaultDb) {
  const { rows } = await db.query(
    `${CONVERSATION_SELECT} WHERE c.client_id = $1 AND st.code = $2 AND c.status = 'OPEN'`,
    [clientId, serviceCode],
  );
  return rows[0] ?? null;
}

/** Least-loaded accepting professional of the type the service requires. */
export async function pickProfessional(serviceCode, db = defaultDb) {
  const { rows } = await db.query(
    `SELECT p.user_id
     FROM professionals p
     JOIN service_types st ON st.required_professional_type = p.professional_type
     JOIN users u ON u.id = p.user_id
     WHERE st.code = $1 AND p.is_accepting_clients AND u.status NOT IN ('SUSPENDED', 'DEACTIVATED')
     ORDER BY (SELECT COUNT(*) FROM conversations c WHERE c.professional_id = p.user_id AND c.status = 'OPEN'),
              p.user_id
     LIMIT 1`,
    [serviceCode],
  );
  return rows[0]?.user_id ?? null;
}

export async function create(tx, { clientId, professionalId, serviceTypeId, title }) {
  const { rows } = await tx.query(
    `INSERT INTO conversations (client_id, professional_id, service_type_id, title)
     VALUES ($1, $2, $3, $4) RETURNING id`,
    [clientId, professionalId, serviceTypeId, title],
  );
  return rows[0].id;
}

export async function listForProfessional(professionalId, db = defaultDb) {
  const { rows } = await db.query(
    `${CONVERSATION_SELECT} WHERE c.professional_id = $1 ORDER BY c.updated_at DESC, c.id DESC`,
    [professionalId],
  );
  return rows;
}

export async function listAll(db = defaultDb) {
  const { rows } = await db.query(`${CONVERSATION_SELECT} ORDER BY c.updated_at DESC, c.id DESC LIMIT 200`);
  return rows;
}

export async function touch(tx, id) {
  await tx.query('UPDATE conversations SET updated_at = now() WHERE id = $1', [id]);
}
