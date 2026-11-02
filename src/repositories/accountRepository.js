import * as defaultDb from '../db.js';
import { AccountFactory } from '../domain/accounts.js';

const ACCOUNT_SELECT = `
  SELECT u.id, u.username, u.email, u.status, u.created_at, u.last_login_at,
         r.code AS role_code,
         up.full_name, up.contact_no, up.address, up.birth_date, up.profile_picture_url,
         cst.code AS client_preferred_service,
         c.emergency_contact_name AS client_emergency_contact_name,
         c.emergency_contact_no   AS client_emergency_contact_no,
         p.display_title AS pro_display_title, p.bio AS pro_bio,
         p.years_of_experience AS pro_years_of_experience,
         p.is_accepting_clients AS pro_is_accepting_clients,
         at.bar_roll_no AS atty_bar_roll_no, at.ibp_no AS atty_ibp_no,
         at.law_firm AS atty_law_firm, at.jurisdiction AS atty_jurisdiction,
         ps.prc_license_no AS psy_prc_license_no, ps.license_expiry AS psy_license_expiry,
         ps.clinic_name AS psy_clinic_name,
         ps.accepts_medication_management AS psy_accepts_medication_management,
         ad.job_title AS admin_job_title, ad.department AS admin_department,
         ad.is_super_admin AS admin_is_super_admin,
         iv.status AS verification_status, iv.id_type_name AS verification_id_type,
         iv.id_number AS verification_id_number,
         iv.rejection_reason AS verification_rejection_reason,
         iv.submitted_at AS verification_submitted_at
  FROM users u
  JOIN roles r                ON r.role_id = u.role_id
  LEFT JOIN user_profiles up  ON up.user_id = u.id
  LEFT JOIN clients c         ON c.user_id = u.id
  LEFT JOIN service_types cst ON cst.service_type_id = c.preferred_service_id
  LEFT JOIN professionals p   ON p.user_id = u.id
  LEFT JOIN attorneys at      ON at.user_id = u.id
  LEFT JOIN psychiatrists ps  ON ps.user_id = u.id
  LEFT JOIN admins ad         ON ad.user_id = u.id
  LEFT JOIN LATERAL (
      SELECT v.status, it.name AS id_type_name, v.id_number, v.rejection_reason, v.submitted_at
      FROM identity_verifications v
      JOIN id_types it ON it.id_type_id = v.id_type_id
      WHERE v.user_id = u.id
      ORDER BY v.submitted_at DESC, v.id DESC
      LIMIT 1
  ) iv ON TRUE`;

const permissionCache = new Map();

export async function permissionsForRole(roleCode, db = defaultDb) {
  if (!permissionCache.has(roleCode)) {
    const { rows } = await db.query(
      `SELECT p.code FROM role_permissions rp
       JOIN roles r ON r.role_id = rp.role_id
       JOIN permissions p ON p.permission_id = rp.permission_id
       WHERE r.code = $1`,
      [roleCode],
    );
    permissionCache.set(roleCode, rows.map((row) => row.code));
  }
  return permissionCache.get(roleCode);
}

async function toAccount(row, db) {
  return row ? AccountFactory.fromRow(row, await permissionsForRole(row.role_code, db)) : null;
}

export async function findById(id, db = defaultDb) {
  const { rows } = await db.query(`${ACCOUNT_SELECT} WHERE u.id = $1`, [id]);
  return toAccount(rows[0], db);
}

/** Login accepts a username, email, or full name (legacy behaviour). Returns id + hash pairs. */
export async function findCredentialsByLoginId(loginId, db = defaultDb) {
  const { rows } = await db.query(
    `SELECT u.id, u.password_hash
     FROM users u LEFT JOIN user_profiles up ON up.user_id = u.id
     WHERE LOWER(u.username) = LOWER($1) OR LOWER(u.email) = LOWER($1) OR LOWER(up.full_name) = LOWER($1)
     ORDER BY u.id`,
    [loginId],
  );
  return rows;
}

export async function findPasswordHash(id, db = defaultDb) {
  const { rows } = await db.query('SELECT password_hash FROM users WHERE id = $1', [id]);
  return rows[0]?.password_hash ?? null;
}

export async function findIdByEmail(email, db = defaultDb) {
  const { rows } = await db.query('SELECT id, email FROM users WHERE LOWER(email) = LOWER($1)', [email]);
  return rows[0] ?? null;
}

export async function touchLastLogin(id, db = defaultDb) {
  await db.query('UPDATE users SET last_login_at = now() WHERE id = $1', [id]);
}

/** Creates users + user_profiles + clients rows. Must run inside a transaction. */
export async function createClient(tx, { username, email, passwordHash, fullName, contactNo, address }) {
  const { rows } = await tx.query(
    `INSERT INTO users (role_id, username, email, password_hash, status)
     SELECT role_id, $1, $2, $3, 'ACTIVE' FROM roles WHERE code = 'CLIENT'
     RETURNING id`,
    [username, email, passwordHash],
  );
  const id = rows[0].id;
  await tx.query(
    `INSERT INTO user_profiles (user_id, full_name, contact_no, address) VALUES ($1, $2, $3, $4)`,
    [id, fullName, contactNo, address],
  );
  await tx.query('INSERT INTO clients (user_id, consent_to_terms_at) VALUES ($1, now())', [id]);
  return id;
}

export async function updateCredentials(id, { username, email, passwordHash }, db = defaultDb) {
  await db.query(
    `UPDATE users SET
       username      = COALESCE(NULLIF($2, ''), username),
       email         = COALESCE(NULLIF($3, ''), email),
       password_hash = COALESCE(NULLIF($4, ''), password_hash)
     WHERE id = $1`,
    [id, username ?? null, email ?? null, passwordHash ?? null],
  );
}

export async function updateProfile(id, { fullName, contactNo, address, profilePictureUrl }, db = defaultDb) {
  await db.query(
    `UPDATE user_profiles SET
       full_name           = COALESCE(NULLIF($2, ''), full_name),
       contact_no          = COALESCE($3, contact_no),
       address             = COALESCE($4, address),
       profile_picture_url = COALESCE(NULLIF($5, ''), profile_picture_url)
     WHERE user_id = $1`,
    [id, fullName ?? null, contactNo ?? null, address ?? null, profilePictureUrl ?? null],
  );
}

export async function setStatus(id, status, db = defaultDb) {
  await db.query('UPDATE users SET status = $2 WHERE id = $1', [id, status]);
}

export async function list({ limit = 50 } = {}, db = defaultDb) {
  const { rows } = await db.query(`${ACCOUNT_SELECT} ORDER BY u.id ASC LIMIT $1`, [limit]);
  return Promise.all(rows.map((row) => toAccount(row, db)));
}

export async function countAll(db = defaultDb) {
  const { rows } = await db.query('SELECT COUNT(*)::int AS n FROM users');
  return rows[0].n;
}
