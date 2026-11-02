import * as defaultDb from '../db.js';

export async function daysWithAppointments(year, month, timezone, db = defaultDb) {
  const { rows } = await db.query(
    `SELECT DISTINCT EXTRACT(DAY FROM scheduled_start AT TIME ZONE $3)::int AS day
     FROM appointments
     WHERE scheduled_start >= make_timestamptz($1, $2, 1, 0, 0, 0, $3)
       AND scheduled_start <  make_timestamptz($1, $2, 1, 0, 0, 0, $3) + INTERVAL '1 month'
       AND status <> 'CANCELLED'
     ORDER BY day`,
    [year, month, timezone],
  );
  return rows.map((row) => row.day);
}

export async function countToday(timezone, db = defaultDb) {
  const { rows } = await db.query(
    `SELECT COUNT(*)::int AS n FROM appointments
     WHERE (scheduled_start AT TIME ZONE $1)::date = (now() AT TIME ZONE $1)::date
       AND status <> 'CANCELLED'`,
    [timezone],
  );
  return rows[0].n;
}
