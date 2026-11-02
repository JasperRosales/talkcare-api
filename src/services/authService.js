import crypto from 'node:crypto';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { config } from '../config.js';
import * as db from '../db.js';
import { withTransaction } from '../db.js';
import { AuthenticationError, ServiceUnavailableError, ValidationError } from '../errors.js';
import * as accounts from '../repositories/accountRepository.js';
import * as resets from '../repositories/passwordResetRepository.js';
import * as mailer from './mailer.js';
import * as v from './validation.js';

const BCRYPT_COST = 10;
const RESET_TTL_MINUTES = 30;

export const hashPassword = (plain) => bcrypt.hash(plain, BCRYPT_COST);

export function issueToken(account) {
  return jwt.sign({ role: account.role }, config.jwtSecret, {
    subject: String(account.id),
    expiresIn: config.jwtExpiresIn,
  });
}

/** Resolves a bearer token to a fresh Account (status and permissions re-read every request). */
export async function authenticateToken(token) {
  let payload;
  try {
    payload = jwt.verify(token, config.jwtSecret);
  } catch {
    throw new AuthenticationError('Your session has expired. Please sign in again.');
  }
  const account = await accounts.findById(Number(payload.sub));
  if (!account || !account.isActive) throw new AuthenticationError('Your session has expired. Please sign in again.');
  return account;
}

const session = (account) => ({ ...account.toJSON(), sessionToken: issueToken(account) });

export async function login({ username, email, password }) {
  const loginId = v.text(username || email, { max: 150 });
  if (!loginId || !password) throw new ValidationError('Enter your username or email and password.');

  for (const candidate of await accounts.findCredentialsByLoginId(loginId)) {
    if (await bcrypt.compare(String(password), candidate.password_hash)) {
      const account = await accounts.findById(candidate.id);
      if (!account.isActive) throw new AuthenticationError('This account is suspended or deactivated.');
      await accounts.touchLastLogin(account.id);
      return session(account);
    }
  }
  throw new AuthenticationError('Invalid username, email, full name, or password.');
}

export async function register(body) {
  const data = {
    username: v.text(body.username, { max: 100 }),
    fullName: v.text(body.fullName, { max: 150 }),
    email: v.email(body.email),
    contactNo: v.text(body.contactNo, { max: 50 }),
    address: v.text(body.address, { max: 500 }),
  };
  v.requireFields(data, 'Please complete all required account fields.');
  const passwordHash = await hashPassword(v.password(body.password));

  const id = await withTransaction((tx) => accounts.createClient(tx, { ...data, passwordHash }));
  return session(await accounts.findById(id));
}

export async function requestPasswordReset(body) {
  if (!mailer.isConfigured()) {
    throw new ServiceUnavailableError('Password recovery email is not configured. Contact the administrator.');
  }
  const email = v.email(body.email);
  const user = email ? await accounts.findIdByEmail(email) : null;
  if (user) {
    const token = crypto.randomBytes(32).toString('base64url');
    const tokenHash = sha256(token);
    await withTransaction((tx) => resets.replaceForUser(tx, { userId: user.id, tokenHash, ttlMinutes: RESET_TTL_MINUTES }));
    try {
      await mailer.sendMail({
        to: user.email,
        subject: 'Reset your TalkCare password',
        text:
          'A password reset was requested for your TalkCare account.\n\n' +
          `Use this one-time link within ${RESET_TTL_MINUTES} minutes:\n${config.resetUrl}/?resetToken=${token}\n\n` +
          'If you did not request this, you can ignore this email.',
      });
    } catch (err) {
      await resets.remove(db, tokenHash);
      console.error('Could not send password reset email:', err.message);
      throw new ServiceUnavailableError('Could not send the password reset email. Check the SMTP configuration and try again.');
    }
  }
  return { message: 'If an account exists for that email, a password reset link has been sent.' };
}

export async function resetPassword(body) {
  const newPassword = v.password(body.newPassword);
  const token = v.text(body.token, { max: 200 });
  const passwordHash = await hashPassword(newPassword);
  const ok = await withTransaction(async (tx) => {
    const userId = token ? await resets.lockUsable(tx, sha256(token)) : null;
    if (!userId) return false;
    await accounts.updateCredentials(userId, { passwordHash }, tx);
    await resets.markUsed(tx, sha256(token));
    return true;
  });
  if (!ok) throw new ValidationError('This reset link is invalid, expired, or already used. Request a new link.');
  return { message: 'Password updated. You can now log in.' };
}

function sha256(value) {
  return crypto.createHash('sha256').update(value).digest('hex');
}
