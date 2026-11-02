import { REPORT_STATUS_LABELS, Role } from '../domain/constants.js';

export function reportJson(r) {
  return {
    id: r.id,
    reportCode: r.report_code,
    userId: r.client_id,
    clientId: r.client_id,
    serviceType: r.service_type,
    fullName: r.full_name,
    email: r.email,
    address: r.address ?? '',
    contactNo: r.contact_no ?? '',
    concernType: r.concern_type,
    involvedWitness: r.involved_witness ?? '',
    concernDetails: r.concern_details,
    status: r.status,
    statusLabel: REPORT_STATUS_LABELS[r.status] ?? r.status,
    remarks: r.remarks ?? '',
    assignedProfessionalId: r.assigned_professional_id,
    createdAt: r.created_at,
    updatedAt: r.updated_at,
  };
}

/** Domain view used for `account.canAccessReport(...)`. */
export const reportRef = (r) => ({ clientId: r.client_id, assignedProfessionalId: r.assigned_professional_id });

export function conversationJson(c) {
  return {
    id: c.id,
    clientId: c.client_id,
    clientName: c.client_name ?? '',
    professionalId: c.professional_id,
    professionalName: c.professional_name ?? '',
    professionalTitle: c.professional_title ?? '',
    professionalType: c.professional_type,
    serviceType: c.service_type,
    title: c.title ?? '',
    status: c.status,
    createdAt: c.created_at,
    updatedAt: c.updated_at,
  };
}

/** Domain view used for `account.canAccessConversation(...)`. */
export const conversationRef = (c) => ({ clientId: c.client_id, professionalId: c.professional_id });

function senderRole(m) {
  if (m.is_auto_reply) return 'SYSTEM';
  if (m.sender_role_code === Role.CLIENT) return 'CLIENT';
  if (m.sender_role_code === Role.ADMIN) return 'ADMIN';
  return 'PROFESSIONAL';
}

export function messageJson(m) {
  const attachments = Array.isArray(m.attachments) ? m.attachments : [];
  return {
    id: m.id,
    conversationId: m.conversation_id,
    senderId: m.sender_id,
    senderName: m.is_auto_reply ? 'TalkCare Auto-Assistant' : (m.sender_name || m.sender_username || ''),
    senderRole: senderRole(m),
    messageText: m.message_text ?? '',
    autoReply: m.is_auto_reply,
    attachmentUrl: attachments[0]?.url ?? '',
    attachments,
    createdAt: m.created_at,
  };
}
