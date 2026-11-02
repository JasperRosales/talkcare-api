import { createApp } from './app.js';
import { config } from './config.js';
import { pool } from './db.js';

const server = createApp().listen(config.port, () => {
  console.log(`TalkCare API listening on http://localhost:${config.port} (db driver: ${config.dbDriver})`);
});

function shutdown(signal) {
  console.log(`${signal} received, shutting down...`);
  server.close(() => pool.end().finally(() => process.exit(0)));
  setTimeout(() => process.exit(1), 10_000).unref();
}
process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));
