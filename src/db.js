import pg from 'pg';
import { Pool as NeonPool, neonConfig } from '@neondatabase/serverless';
import ws from 'ws';
import { config } from './config.js';

/**
 * Connection provider. Both drivers expose the node-postgres Pool API, so the rest of the
 * code is driver-agnostic:
 *  - "neon": @neondatabase/serverless over WebSockets (works on serverless hosts).
 *  - "pg":   plain TCP node-postgres (local Postgres, long-running servers).
 */
export function createPool(connectionString = config.databaseUrl, driver = config.dbDriver) {
  if (driver === 'neon') {
    neonConfig.webSocketConstructor = ws;
    return new NeonPool({ connectionString, max: config.dbPoolMax });
  }
  return new pg.Pool({ connectionString, max: config.dbPoolMax });
}

export const pool = createPool();

pool.on('error', (err) => {
  console.error('Idle database client error:', err.message);
});

/** Run a single parameterized query. */
export function query(text, params = []) {
  return pool.query(text, params);
}

/** Run `work(client)` inside BEGIN/COMMIT; rolls back on any thrown error. */
export async function withTransaction(work) {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const result = await work(client);
    await client.query('COMMIT');
    return result;
  } catch (err) {
    try {
      await client.query('ROLLBACK');
    } catch (rollbackErr) {
      console.error('Rollback failed:', rollbackErr.message);
    }
    throw err;
  } finally {
    client.release();
  }
}
