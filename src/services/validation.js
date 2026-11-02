import { ValidationError } from '../errors.js';

export function text(value, { max = 5000 } = {}) {
  if (value == null) return '';
  const s = String(value).trim();
  if (s.length > max) throw new ValidationError(`A field is longer than ${max} characters.`);
  return s;
}

export function requireFields(values, message) {
  for (const value of Object.values(values)) {
    if (!value) throw new ValidationError(message);
  }
}

export function email(value) {
  const s = text(value, { max: 150 });
  if (s && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s)) throw new ValidationError('Please enter a valid email address.');
  return s;
}

export function password(value, { optional = false } = {}) {
  const s = value == null ? '' : String(value);
  if (optional && s === '') return '';
  if (s.length < 8) throw new ValidationError('Password must be at least 8 characters long.');
  if (s.length > 72) throw new ValidationError('Password must be at most 72 characters long.');
  return s;
}

export function positiveInt(value, name = 'id') {
  const n = Number(value);
  if (!Number.isInteger(n) || n <= 0) throw new ValidationError(`Invalid ${name}.`);
  return n;
}
