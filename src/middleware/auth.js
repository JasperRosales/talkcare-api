import { AuthenticationError, ForbiddenError } from '../errors.js';
import { authenticateToken } from '../services/authService.js';

function readToken(req) {
  const header = req.get('authorization') ?? '';
  if (/^bearer\s+/i.test(header)) return header.replace(/^bearer\s+/i, '').trim();
  return req.get('x-session-token')?.trim() || null;
}

/** Requires a valid session token and attaches the resolved Account as `req.actor`. */
export async function authenticate(req, _res, next) {
  const token = readToken(req);
  if (!token) return next(new AuthenticationError());
  req.actor = await authenticateToken(token);
  return next();
}

/** Permission gate; polymorphic resource checks happen in the services. */
export const requirePermission = (...permissions) => (req, _res, next) => {
  if (!req.actor) return next(new AuthenticationError());
  if (!permissions.some((p) => req.actor.can(p))) return next(new ForbiddenError());
  return next();
};
