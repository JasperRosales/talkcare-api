import { withTransaction } from '../db.js';
import { Client, Professional } from '../domain/accounts.js';
import { AUTO_REPLY_TEXT, Permission, ServiceType } from '../domain/constants.js';
import { ConflictError, ForbiddenError, NotFoundError, ServiceUnavailableError, ValidationError } from '../errors.js';
import * as conversations from '../repositories/conversationRepository.js';
import * as lookups from '../repositories/lookupRepository.js';
import * as messages from '../repositories/messageRepository.js';
import { conversationJson, conversationRef, messageJson } from './serializers.js';
import { storage, IMAGE_TYPES } from './storage.js';
import * as v from './validation.js';

const TITLES = { JUSTICE_CARE: 'JusticeCare Support', THOMATT: 'ThoMatt Consultation' };

function welcomeText(serviceCode, professionalName) {
  return serviceCode === ServiceType.JUSTICE_CARE
    ? `Hello, I am ${professionalName} from JusticeCare. How can we support and protect your rights today?`
    : `Welcome to ThoMatt. I am ${professionalName}. This is a safe and confidential environment. Tell me your thoughts...`;
}

/** Returns the client's open conversation for a service, creating it (with a welcome message) if needed. */
export async function openConversation(actor, serviceType) {
  if (!(actor instanceof Client) || !actor.can(Permission.CONVERSATIONS_CREATE)) {
    throw new ForbiddenError('Only client accounts can open a conversation.');
  }
  const serviceCode = ServiceType[serviceType];
  if (!serviceCode) throw new ValidationError('Please choose a valid TalkCare service.');

  const existing = await conversations.findOpen(actor.id, serviceCode);
  if (existing) return conversationJson(existing);

  const service = await lookups.findServiceType(serviceCode);
  const professionalId = await conversations.pickProfessional(serviceCode);
  if (!professionalId) throw new ServiceUnavailableError('No professional is currently available for this service.');

  try {
    const id = await withTransaction(async (tx) => {
      const conversationId = await conversations.create(tx, {
        clientId: actor.id,
        professionalId,
        serviceTypeId: service.service_type_id,
        title: TITLES[serviceCode],
      });
      const created = await conversations.findById(conversationId, tx);
      await messages.create(tx, {
        conversationId,
        senderId: professionalId,
        text: welcomeText(serviceCode, created.professional_name || 'your assigned professional'),
      });
      return conversationId;
    });
    return conversationJson(await conversations.findById(id));
  } catch (err) {
    // Concurrent request created it first (conversations_one_open_per_service_uk).
    if (err.code === '23505') {
      const winner = await conversations.findOpen(actor.id, serviceCode);
      if (winner) return conversationJson(winner);
    }
    throw err;
  }
}

async function loadAccessible(actor, conversationId) {
  const conversation = await conversations.findById(v.positiveInt(conversationId, 'conversation id'));
  if (!conversation || !actor.canAccessConversation(conversationRef(conversation))) {
    throw new NotFoundError('The conversation could not be found.');
  }
  return conversation;
}

export async function listAssigned(actor) {
  if (actor instanceof Professional && actor.can(Permission.CONVERSATIONS_READ_ASSIGNED)) {
    return (await conversations.listForProfessional(actor.id)).map(conversationJson);
  }
  if (actor.can(Permission.USERS_MANAGE)) return (await conversations.listAll()).map(conversationJson);
  throw new ForbiddenError();
}

export async function getConversation(actor, conversationId) {
  return conversationJson(await loadAccessible(actor, conversationId));
}

export async function listMessages(actor, conversationId) {
  const conversation = await loadAccessible(actor, conversationId);
  return (await messages.listByConversation(conversation.id)).map(messageJson);
}

/** Client message + automatic acknowledgement, atomically. Returns the two new messages. */
export async function sendClientMessage(actor, conversationId, body) {
  if (!actor.can(Permission.MESSAGES_SEND)) throw new ForbiddenError();
  const conversation = await loadAccessible(actor, conversationId);
  if (conversation.client_id !== actor.id) throw new ForbiddenError('Only the client can send messages here.');
  if (conversation.status !== 'OPEN') throw new ConflictError('This conversation is closed.');

  const text = v.text(body.messageText, { max: 5000 });
  if (!text && !body.attachmentData) throw new ValidationError('Write a message or attach a photo before sending.');

  const attachment = body.attachmentData
    ? await storage.saveDataUrl(body.attachmentData, {
        prefix: 'chat',
        allowedTypes: IMAGE_TYPES.PHOTO,
        label: 'photo',
      })
    : null;

  try {
    const ids = await withTransaction(async (tx) => {
      const own = await messages.create(tx, { conversationId: conversation.id, senderId: actor.id, text, attachment });
      const auto = await messages.create(tx, { conversationId: conversation.id, text: AUTO_REPLY_TEXT, autoReply: true });
      await conversations.touch(tx, conversation.id);
      return [own, auto];
    });
    return (await messages.listByIds(ids)).map(messageJson);
  } catch (err) {
    if (attachment) await storage.remove(attachment.url);
    throw err;
  }
}

export async function sendProfessionalReply(actor, conversationId, body) {
  if (!actor.can(Permission.MESSAGES_REPLY)) throw new ForbiddenError();
  const conversation = await loadAccessible(actor, conversationId);
  const text = v.text(body.messageText, { max: 5000 });
  if (!text) throw new ValidationError('Write a reply before sending.');

  const id = await withTransaction(async (tx) => {
    const messageId = await messages.create(tx, { conversationId: conversation.id, senderId: actor.id, text });
    await conversations.touch(tx, conversation.id);
    return messageId;
  });
  return messageJson((await messages.listByIds([id]))[0]);
}
