import * as defaultDb from '../db.js';

export async function findServiceType(code, db = defaultDb) {
  const { rows } = await db.query(
    'SELECT service_type_id, code, display_name, required_professional_type FROM service_types WHERE code = $1',
    [code],
  );
  return rows[0] ?? null;
}

export async function findConcernType(name, db = defaultDb) {
  const { rows } = await db.query(
    `SELECT ct.concern_type_id, ct.name, st.code AS service_code
     FROM concern_types ct LEFT JOIN service_types st ON st.service_type_id = ct.service_type_id
     WHERE LOWER(ct.name) = LOWER($1)`,
    [name],
  );
  return rows[0] ?? null;
}

export async function findIdType(name, db = defaultDb) {
  const { rows } = await db.query('SELECT id_type_id, name FROM id_types WHERE LOWER(name) = LOWER($1)', [name]);
  return rows[0] ?? null;
}

export async function listAll(db = defaultDb) {
  const [services, concerns, idTypes] = await Promise.all([
    db.query('SELECT code, display_name FROM service_types ORDER BY service_type_id'),
    db.query(
      `SELECT ct.name, st.code AS service_code FROM concern_types ct
       LEFT JOIN service_types st ON st.service_type_id = ct.service_type_id ORDER BY ct.concern_type_id`,
    ),
    db.query('SELECT name FROM id_types ORDER BY id_type_id'),
  ]);
  return {
    serviceTypes: services.rows.map((r) => ({ code: r.code, displayName: r.display_name })),
    concernTypes: concerns.rows.map((r) => ({ name: r.name, serviceType: r.service_code })),
    idTypes: idTypes.rows.map((r) => r.name),
  };
}
