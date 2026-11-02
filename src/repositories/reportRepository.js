import * as defaultDb from '../db.js';

const REPORT_SELECT = `
  SELECT r.id, r.report_code, r.client_id, r.status, r.involved_witness, r.concern_details,
         r.remarks, r.assigned_professional_id, r.created_at, r.updated_at,
         st.code AS service_type, ct.name AS concern_type,
         COALESCE(s.full_name, up.full_name)   AS full_name,
         COALESCE(s.email, u.email)            AS email,
         COALESCE(s.contact_no, up.contact_no) AS contact_no,
         COALESCE(s.address, up.address)       AS address
  FROM reports r
  JOIN service_types st      ON st.service_type_id = r.service_type_id
  JOIN concern_types ct      ON ct.concern_type_id = r.concern_type_id
  JOIN users u               ON u.id = r.client_id
  LEFT JOIN user_profiles up ON up.user_id = r.client_id
  LEFT JOIN report_snapshots s ON s.report_id = r.id`;

/**
 * Inserts a report with a code derived from its id (RPT-0001) and the immutable reporter snapshot.
 * Must run inside a transaction.
 */
export async function create(tx, report) {
  const { rows } = await tx.query(
    `WITH next AS (SELECT nextval(pg_get_serial_sequence('reports', 'id')) AS id)
     INSERT INTO reports (id, report_code, client_id, service_type_id, concern_type_id,
                          involved_witness, concern_details)
     SELECT id, 'RPT-' || LPAD(id::text, GREATEST(4, LENGTH(id::text)), '0'), $1, $2, $3, $4, $5
     FROM next
     RETURNING id`,
    [report.clientId, report.serviceTypeId, report.concernTypeId, report.involvedWitness, report.concernDetails],
  );
  const id = rows[0].id;
  await tx.query(
    `INSERT INTO report_snapshots (report_id, full_name, email, contact_no, address) VALUES ($1, $2, $3, $4, $5)`,
    [id, report.fullName, report.email, report.contactNo, report.address],
  );
  return id;
}

export async function findById(id, db = defaultDb) {
  const { rows } = await db.query(`${REPORT_SELECT} WHERE r.id = $1`, [id]);
  return rows[0] ?? null;
}

/** scope: { all: true } | { clientId } | { professionalId } */
export async function list(scope, status, db = defaultDb) {
  const where = [];
  const params = [];
  if (scope.clientId != null) {
    params.push(scope.clientId);
    where.push(`r.client_id = $${params.length}`);
  } else if (scope.professionalId != null) {
    params.push(scope.professionalId);
    where.push(`r.assigned_professional_id = $${params.length}`);
  } else if (!scope.all) {
    return [];
  }
  if (status) {
    params.push(status);
    where.push(`r.status = $${params.length}`);
  }
  const clause = where.length ? `WHERE ${where.join(' AND ')}` : '';
  const { rows } = await db.query(`${REPORT_SELECT} ${clause} ORDER BY r.id DESC`, params);
  return rows;
}

export async function listByStatus(status, db = defaultDb) {
  const { rows } = await db.query(`${REPORT_SELECT} WHERE r.status = $1 ORDER BY r.created_at DESC`, [status]);
  return rows;
}

export async function lockWithStatus(tx, id, status) {
  const { rows } = await tx.query(
    `SELECT id, client_id, report_code FROM reports WHERE id = $1 AND status = $2 FOR UPDATE`,
    [id, status],
  );
  return rows[0] ?? null;
}

export async function lock(tx, id) {
  const { rows } = await tx.query('SELECT id, client_id, report_code, status FROM reports WHERE id = $1 FOR UPDATE', [id]);
  return rows[0] ?? null;
}

export async function updateStatus(tx, id, status) {
  await tx.query('UPDATE reports SET status = $2 WHERE id = $1', [id, status]);
}

export async function appendRemarks(tx, id, remarks) {
  await tx.query(
    `UPDATE reports SET remarks = CASE WHEN COALESCE(remarks, '') = '' THEN $2 ELSE remarks || E'\\n' || $2 END
     WHERE id = $1`,
    [id, remarks],
  );
}

export async function countByStatus(status, db = defaultDb) {
  const { rows } = await db.query('SELECT COUNT(*)::int AS n FROM reports WHERE status = $1', [status]);
  return rows[0].n;
}
