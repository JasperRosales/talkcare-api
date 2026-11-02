/** Canonical codes mirrored from the lookup tables in schema.md §5. */

export const Role = Object.freeze({
  CLIENT: 'CLIENT',
  ATTORNEY: 'ATTORNEY',
  PSYCHIATRIST: 'PSYCHIATRIST',
  ADMIN: 'ADMIN',
});

export const Permission = Object.freeze({
  REPORTS_CREATE: 'reports.create',
  REPORTS_READ_OWN: 'reports.read.own',
  REPORTS_READ_ASSIGNED: 'reports.read.assigned',
  REPORTS_READ_ALL: 'reports.read.all',
  REPORTS_UPDATE_STATUS: 'reports.update.status',
  CONVERSATIONS_CREATE: 'conversations.create',
  CONVERSATIONS_READ_OWN: 'conversations.read.own',
  CONVERSATIONS_READ_ASSIGNED: 'conversations.read.assigned',
  MESSAGES_SEND: 'messages.send',
  MESSAGES_REPLY: 'messages.reply',
  APPOINTMENTS_BOOK_OWN: 'appointments.book.own',
  APPOINTMENTS_READ_ASSIGNED: 'appointments.read.assigned',
  APPOINTMENTS_MANAGE_ASSIGNED: 'appointments.manage.assigned',
  USERS_READ: 'users.read',
  USERS_MANAGE: 'users.manage',
  VERIFICATIONS_SUBMIT: 'verifications.submit',
  VERIFICATIONS_REVIEW: 'verifications.review',
  ADMIN_DASHBOARD: 'admin.dashboard',
  ADMIN_ACTIVITY_READ: 'admin.activity.read',
});

export const ServiceType = Object.freeze({
  JUSTICE_CARE: 'JUSTICE_CARE',
  THOMATT: 'THOMATT',
});

export const ReportStatus = Object.freeze({
  UNDER_REVIEW: 'UNDER_REVIEW',
  IN_PROGRESS: 'IN_PROGRESS',
  RESOLVED: 'RESOLVED',
  REJECTED: 'REJECTED',
});

export const REPORT_STATUS_LABELS = Object.freeze({
  UNDER_REVIEW: 'Under Review',
  IN_PROGRESS: 'In Progress',
  RESOLVED: 'Resolved',
  REJECTED: 'Rejected',
});

/** Accepts "Under Review", "under_review", "IN_PROGRESS", ... and returns the canonical code. */
export function normalizeReportStatus(value) {
  if (value == null) return null;
  const code = String(value).trim().toUpperCase().replace(/[\s-]+/g, '_');
  return Object.hasOwn(ReportStatus, code) ? code : null;
}

export const AUTO_REPLY_TEXT =
  'thank you for your message, please wait for the reply of the assigned Professional';
