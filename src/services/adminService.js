import { config } from '../config.js';
import { withTransaction } from '../db.js';
import { Permission, REPORT_STATUS_LABELS, ReportStatus } from '../domain/constants.js';
import { ConflictError, ForbiddenError, ValidationError } from '../errors.js';
import * as accounts from '../repositories/accountRepository.js';
import * as activity from '../repositories/activityRepository.js';
import * as appointments from '../repositories/appointmentRepository.js';
import * as reports from '../repositories/reportRepository.js';
import * as verifications from '../repositories/verificationRepository.js';
import * as v from './validation.js';

export async function metrics() {
  const [totalUsers, pendingVerifications, pendingReports, sessionToday] = await Promise.all([
    accounts.countAll(),
    verifications.countPending(),
    reports.countByStatus(ReportStatus.UNDER_REVIEW),
    appointments.countToday(config.timezone),
  ]);
  return { totalUsers, pendingApprovals: pendingVerifications + pendingReports, sessionToday };
}

export async function users() {
  return (await accounts.list({ limit: 50 })).map((account) => account.toJSON());
}

/** Pending identity verifications and under-review reports, newest first. */
export async function pendingRequests(actor) {
  const [verificationRows, reportRows] = await Promise.all([
    actor.can(Permission.VERIFICATIONS_REVIEW) ? verifications.listPending() : [],
    actor.can(Permission.REPORTS_UPDATE_STATUS) ? reports.listByStatus(ReportStatus.UNDER_REVIEW) : [],
  ]);
  const requests = [
    ...verificationRows.map((r) => ({
      type: 'VERIFICATION',
      id: r.id,
      userId: r.user_id,
      name: r.full_name,
      email: r.email,
      idType: r.id_type,
      idNumber: r.id_number,
      address: r.address,
      contactNo: r.contact_no,
      imageUrl: r.document_url,
      status: r.status,
      submittedAt: r.submitted_at,
    })),
    ...reportRows.map((r) => ({
      type: 'REPORT',
      id: r.id,
      userId: r.client_id,
      name: r.full_name,
      email: r.email,
      address: r.address,
      contactNo: r.contact_no,
      status: r.status,
      submittedAt: r.created_at,
      reportCode: r.report_code,
      concernType: r.concern_type,
      details: r.concern_details,
      witness: r.involved_witness,
      remarks: r.remarks,
    })),
  ];
  return requests.sort((a, b) => new Date(b.submittedAt) - new Date(a.submittedAt));
}

const VERIFICATION_ACTIONS = new Set(['APPROVE', 'REJECT']);
const REPORT_ACTIONS = new Set(['IN_PROGRESS', 'RESOLVED', 'ADD_REMARKS']);

export async function processRequest(actor, body) {
  const id = v.positiveInt(body.id, 'request id');
  const action = String(body.action ?? '');
  const reason = v.text(body.reason, { max: 2000 });
  const remarks = v.text(body.remarks, { max: 2000 });

  if (body.type === 'VERIFICATION') {
    if (!actor.can(Permission.VERIFICATIONS_REVIEW)) throw new ForbiddenError();
    if (!VERIFICATION_ACTIONS.has(action)) throw new ValidationError('Invalid verification action.');
    if (action === 'REJECT' && !reason) throw new ValidationError('A rejection reason is required.');
    return reviewVerification(actor, id, action, reason);
  }
  if (body.type === 'REPORT') {
    if (!actor.can(Permission.REPORTS_UPDATE_STATUS)) throw new ForbiddenError();
    if (!REPORT_ACTIONS.has(action)) throw new ValidationError('Invalid report action.');
    if (action === 'ADD_REMARKS' && !remarks) throw new ValidationError('Please enter remarks before saving.');
    return reviewReport(actor, id, action, remarks);
  }
  throw new ValidationError('Unknown request type.');
}

async function reviewVerification(actor, id, action, reason) {
  const approved = action === 'APPROVE';
  await withTransaction(async (tx) => {
    const request = await verifications.lockPending(tx, id);
    if (!request) throw new ConflictError('This verification request is no longer pending.');
    await verifications.review(tx, {
      id,
      status: approved ? 'APPROVED' : 'REJECTED',
      reviewerId: actor.id,
      rejectionReason: approved ? null : reason,
    });
    // Rejected users go back to ACTIVE so they can resubmit; users.status has no REJECTED value.
    await accounts.setStatus(request.user_id, approved ? 'VERIFIED' : 'ACTIVE', tx);
    const subject = request.full_name || 'User';
    await activity.log(tx, {
      actorId: actor.id,
      entityType: 'VERIFICATION',
      entityId: id,
      action,
      details: approved
        ? `Verification approved for ${subject}`
        : `Verification rejected for ${subject}. Reason: ${reason}`,
    });
    await activity.notify(tx, {
      userId: request.user_id,
      type: 'VERIFICATION',
      message: approved
        ? 'Your account verification has been approved.'
        : `Your account verification was rejected: ${reason}`,
    });
  });
  return { success: true };
}

async function reviewReport(actor, id, action, remarks) {
  await withTransaction(async (tx) => {
    const report = await reports.lockWithStatus(tx, id, ReportStatus.UNDER_REVIEW);
    if (!report) throw new ConflictError('This report is no longer pending.');
    let details;
    let notification;
    if (action === 'ADD_REMARKS') {
      await reports.appendRemarks(tx, id, remarks);
      details = `Remarks added to ${report.report_code}. Remarks: ${remarks}`;
      notification = `A TalkCare admin added remarks to your report ${report.report_code}.`;
    } else {
      await reports.updateStatus(tx, id, action);
      const label = REPORT_STATUS_LABELS[action];
      details = `Report ${report.report_code} marked ${label}`;
      notification = `Your report ${report.report_code} is now marked ${label}.`;
    }
    await activity.log(tx, { actorId: actor.id, entityType: 'REPORT', entityId: id, action, details });
    await activity.notify(tx, { userId: report.client_id, type: 'REPORT', message: notification });
  });
  return { success: true };
}

export async function recentActivity() {
  return (await activity.recent(12)).map((a) => ({
    action: a.action,
    details: a.details,
    entityType: a.entity_type,
    entityId: a.entity_id,
    actorName: a.actor_name,
    createdAt: a.created_at,
  }));
}

export async function schedule(query) {
  const now = new Date();
  const year = query.year ? v.positiveInt(query.year, 'year') : now.getFullYear();
  const month = query.month ? v.positiveInt(query.month, 'month') : now.getMonth() + 1;
  if (month > 12) throw new ValidationError('Invalid month.');
  const days = await appointments.daysWithAppointments(year, month, config.timezone);
  const monthName = new Date(Date.UTC(year, month - 1, 1)).toLocaleString('en-US', { month: 'long', timeZone: 'UTC' });
  return { year, month, monthName, days };
}
