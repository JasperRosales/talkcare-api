import { withTransaction } from '../db.js';
import { ValidationError } from '../errors.js';
import * as accounts from '../repositories/accountRepository.js';
import * as activity from '../repositories/activityRepository.js';
import * as lookups from '../repositories/lookupRepository.js';
import * as verifications from '../repositories/verificationRepository.js';
import { storage, IMAGE_TYPES } from './storage.js';
import * as v from './validation.js';

export async function submit(actor, body) {
  const data = {
    idType: v.text(body.idType, { max: 100 }),
    idNumber: v.text(body.idNumber, { max: 100 }),
    address: v.text(body.address, { max: 500 }),
    email: v.email(body.email),
    image: body.idDocImage,
  };
  v.requireFields(data, 'ID type, ID number, address, email, and an ID image are required.');

  const idType = await lookups.findIdType(data.idType);
  if (!idType) throw new ValidationError(`"${data.idType}" is not an accepted ID type.`);

  const saved = await storage.saveDataUrl(data.image, {
    prefix: `id_${actor.id}`,
    allowedTypes: IMAGE_TYPES.DOCUMENT,
    maxBytes: 8 * 1024 * 1024,
    label: 'ID image',
  });

  let previousUrl = null;
  try {
    await withTransaction(async (tx) => {
      const result = await verifications.upsertPending(tx, {
        userId: actor.id,
        idTypeId: idType.id_type_id,
        idNumber: data.idNumber,
        documentUrl: saved.url,
      });
      previousUrl = result.previousUrl;
      await accounts.updateCredentials(actor.id, { email: data.email }, tx);
      await accounts.updateProfile(actor.id, { address: data.address }, tx);
      await accounts.setStatus(actor.id, 'PENDING_VERIFICATION', tx);
      await activity.log(tx, {
        actorId: actor.id,
        entityType: 'VERIFICATION',
        entityId: result.id,
        action: 'SUBMITTED',
        details: `Identity verification submitted by ${actor.displayName}`,
      });
    });
  } catch (err) {
    await storage.remove(saved.url);
    throw err;
  }
  if (previousUrl) await storage.remove(previousUrl);
  return { success: true, status: 'PENDING_VERIFICATION' };
}
