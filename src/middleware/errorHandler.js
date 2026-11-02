import { AppError } from '../errors.js';

/** Maps PostgreSQL SQLSTATEs raised by constraints/triggers to API errors. */
const PG_ERRORS = {
  23505: [409, 'CONFLICT', 'That value is already in use (username, email, or license number).'],
  23503: [400, 'VALIDATION_ERROR', 'A referenced record does not exist.'],
  23514: [400, 'VALIDATION_ERROR', 'The request violates a data rule.'],
  '23P01': [409, 'CONFLICT', 'That time slot is already booked.'],
  '22P02': [400, 'VALIDATION_ERROR', 'A value has an invalid format.'],
};

export function notFound(req, res) {
  res.status(404).json({ error: `Unknown endpoint: ${req.method} ${req.path}`, code: 'NOT_FOUND' });
}

// eslint-disable-next-line no-unused-vars
export function errorHandler(err, req, res, _next) {
  if (err instanceof AppError) {
    return res.status(err.status).json({ error: err.message, code: err.code });
  }
  if (err.type === 'entity.parse.failed') {
    return res.status(400).json({ error: 'The request body is not valid JSON.', code: 'VALIDATION_ERROR' });
  }
  if (err.type === 'entity.too.large') {
    return res.status(413).json({ error: 'The upload is too large.', code: 'PAYLOAD_TOO_LARGE' });
  }
  if (err.expose && err.status >= 400 && err.status < 500) {
    // http-errors raised by Express itself (e.g. missing file under /uploads).
    return res.status(err.status).json({ error: err.status === 404 ? 'Not found.' : err.message, code: 'HTTP_ERROR' });
  }
  const mapped = PG_ERRORS[err.code];
  if (mapped) {
    const [status, code, fallback] = mapped;
    // Trigger messages (RAISE EXCEPTION) are written for humans; constraint names are not.
    const message = err.code === '23514' && err.where?.includes('PL/pgSQL') ? err.message : fallback;
    return res.status(status).json({ error: message, code });
  }
  console.error(`${req.method} ${req.path} failed:`, err);
  return res.status(500).json({ error: 'Something went wrong on the TalkCare server.', code: 'INTERNAL_ERROR' });
}
