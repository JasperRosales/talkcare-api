import { withTransaction } from '../db.js';
import { ValidationError } from '../errors.js';
import * as accounts from '../repositories/accountRepository.js';
import { hashPassword } from './authService.js';
import { storage, IMAGE_TYPES } from './storage.js';
import * as v from './validation.js';

export async function updateProfile(actor, body) {
  const username = v.text(body.username, { max: 100 });
  const email = v.email(body.email);
  const newPassword = v.password(body.password, { optional: true });
  const passwordHash = newPassword ? await hashPassword(newPassword) : null;
  const profile = {
    fullName: v.text(body.fullName, { max: 150 }),
    contactNo: body.contactNo === undefined ? null : v.text(body.contactNo, { max: 50 }),
    address: body.address === undefined ? null : v.text(body.address, { max: 500 }),
  };

  await withTransaction(async (tx) => {
    await accounts.updateCredentials(actor.id, { username, email, passwordHash }, tx);
    await accounts.updateProfile(actor.id, profile, tx);
  });
  return accounts.findById(actor.id);
}

export async function uploadPhoto(actor, body) {
  if (!body.image) throw new ValidationError('Choose a photo to upload.');
  const saved = await storage.saveDataUrl(body.image, {
    prefix: `profile_${actor.id}`,
    allowedTypes: IMAGE_TYPES.PHOTO,
    label: 'profile photo',
  });
  const previous = actor.profile.profilePictureUrl;
  await accounts.updateProfile(actor.id, { profilePictureUrl: saved.url });
  if (previous && previous !== saved.url && previous.includes('/uploads/')) await storage.remove(previous);
  return { url: saved.url };
}
