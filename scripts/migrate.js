/**
 * Applies db/migrations/*.sql in filename order, once each, recording them in schema_migrations.
 * Each file runs as a whole (simple-query protocol) inside its own transaction, so PL/pgSQL
 * bodies with semicolons are safe. Uses DATABASE_URL_UNPOOLED when set (recommended on Neon).
 */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import 'dotenv/config';

const dir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'db', 'migrations');
const connectionString = process.env.DATABASE_URL_UNPOOLED?.trim() || process.env.DATABASE_URL?.trim();
if (!connectionString) {
  console.error('Set DATABASE_URL (or DATABASE_URL_UNPOOLED) first. See .env.example.');
  process.exit(1);
}

const { createPool } = await import('../src/db.js');
const driver = process.env.DB_DRIVER?.trim() || (/\.neon\.tech/i.test(connectionString) ? 'neon' : 'pg');
const pool = createPool(connectionString, driver);
const client = await pool.connect();

try {
  await client.query(`CREATE TABLE IF NOT EXISTS schema_migrations (
    version    TEXT PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
  )`);
  const applied = new Set((await client.query('SELECT version FROM schema_migrations')).rows.map((r) => r.version));
  const files = (await fs.readdir(dir)).filter((f) => f.endsWith('.sql')).sort();

  let count = 0;
  for (const file of files) {
    if (applied.has(file)) continue;
    const sql = await fs.readFile(path.join(dir, file), 'utf8');
    process.stdout.write(`Applying ${file} ... `);
    try {
      await client.query('BEGIN');
      await client.query(sql);
      await client.query('INSERT INTO schema_migrations (version) VALUES ($1)', [file]);
      await client.query('COMMIT');
      console.log('done');
      count += 1;
    } catch (err) {
      await client.query('ROLLBACK');
      console.log('FAILED');
      throw err;
    }
  }
  console.log(count ? `Applied ${count} migration(s).` : 'Database is up to date.');
} catch (err) {
  console.error(err.message);
  process.exitCode = 1;
} finally {
  client.release();
  await pool.end();
}
