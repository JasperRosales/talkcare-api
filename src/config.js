import 'dotenv/config';
import path from 'node:path';

function required(name) {
  const value = process.env[name];
  if (!value || !value.trim()) {
    throw new Error(`Missing required environment variable ${name}. See api/.env.example.`);
  }
  return value.trim();
}

function optional(name, fallback = '') {
  const value = process.env[name];
  return value && value.trim() ? value.trim() : fallback;
}

const databaseUrl = required('DATABASE_URL');
const jwtSecret = required('JWT_SECRET');
if (process.env.NODE_ENV === 'production' && (jwtSecret === 'change-me' || jwtSecret.length < 32)) {
  throw new Error('JWT_SECRET must be a random string of at least 32 characters in production.');
}

const smtp = {
  host: optional('TALKCARE_SMTP_HOST'),
  port: Number(optional('TALKCARE_SMTP_PORT', '587')),
  username: optional('TALKCARE_SMTP_USERNAME'),
  password: optional('TALKCARE_SMTP_PASSWORD'),
  from: optional('TALKCARE_SMTP_FROM'),
};

export const config = Object.freeze({
  port: Number(optional('PORT', '4000')),
  databaseUrl,
  databaseUrlUnpooled: optional('DATABASE_URL_UNPOOLED'),
  dbDriver: optional('DB_DRIVER', /\.neon\.tech/i.test(databaseUrl) ? 'neon' : 'pg'),
  dbPoolMax: Number(optional('DB_POOL_MAX', '10')),
  corsOrigins: optional('CORS_ORIGIN', '*').split(',').map((o) => o.trim()).filter(Boolean),
  publicBaseUrl: optional('PUBLIC_BASE_URL').replace(/\/+$/, ''),
  jwtSecret,
  jwtExpiresIn: optional('JWT_EXPIRES_IN', '12h'),
  uploadDir: path.resolve(optional('UPLOAD_DIR', './uploads')),
  timezone: optional('APP_TIMEZONE', 'Asia/Manila'),
  smtp: Object.freeze({ ...smtp, configured: Boolean(smtp.host && smtp.username && smtp.password && smtp.from) }),
  resetUrl: optional('TALKCARE_RESET_URL', 'http://localhost:54321').replace(/\/+$/, ''),
});
