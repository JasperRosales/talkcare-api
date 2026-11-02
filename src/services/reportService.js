import { withTransaction } from '../db.js';
import { Client, Professional } from '../domain/accounts.js';
import { normalizeReportStatus, Permission, REPORT_STATUS_LABELS, ServiceType } from '../domain/constants.js';
import { ForbiddenError, NotFoundError, ValidationError } from '../errors.js';
import * as activity from '../repositories/activityRepository.js';
import * as lookups from '../repositories/lookupRepository.js';
import * as reports from '../repositories/reportRepository.js';
import { reportJson, reportRef } from './serializers.js';
import * as v from './validation.js';

export async function submit(actor, body) {
  // reports.client_id must reference a CLIENT (enforced by the validate_report trigger).
  if (!(actor instanceof Client) || !actor.can(Permission.REPORTS_CREATE)) {
    throw new ForbiddenError('Only client accounts can submit a concern report.');
  }
  const data = {
    fullName: v.text(body.fullName, { max: 150 }),
    email: v.email(body.email),
    address: v.text(body.address, { max: 500 }),
    contactNo: v.text(body.contactNo, { max: 50 }),
    concernType: v.text(body.concernType, { max: 100 }),
    concernDetails: v.text(body.concernDetails, { max: 10000 }),
  };
  v.requireFields(data, 'Please complete all required report fields.');

  const concern = await lookups.findConcernType(data.concernType);
  if (!concern) throw new ValidationError(`"${data.concernType}" is not a recognised concern type.`);
  const serviceCode = concern.service_code ?? (ServiceType[body.serviceType] ? body.serviceType : ServiceType.JUSTICE_CARE);
  const service = await lookups.findServiceType(serviceCode);

  const id = await withTransaction(async (tx) => {
    const reportId = await reports.create(tx, {
      ...data,
      clientId: actor.id,
      serviceTypeId: service.service_type_id,
      concernTypeId: concern.concern_type_id,
      involvedWitness: v.text(body.involvedWitness, { max: 150 }) || null,
    });
    await activity.log(tx, {
      actorId: actor.id,
      entityType: 'REPORT',
      entityId: reportId,
      action: 'SUBMITTED',
      details: `Report submitted by ${actor.displayName} (${concern.name})`,
    });
    return reportId;
  });
  return reportJson(await reports.findById(id));
}

/** Lists the reports visible to the actor; the scope follows from the actor's permissions. */
export async function list(actor, filter) {
  const status = !filter || /^all$/i.test(filter) ? null : normalizeReportStatus(filter);
  if (filter && !/^all$/i.test(filter) && !status) throw new ValidationError('Unknown report status filter.');

  let scope;
  if (actor.can(Permission.REPORTS_READ_ALL)) scope = { all: true };
  else if (actor instanceof Professional && actor.can(Permission.REPORTS_READ_ASSIGNED)) scope = { professionalId: actor.id };
  else if (actor.can(Permission.REPORTS_READ_OWN)) scope = { clientId: actor.id };
  else throw new ForbiddenError();

  return (await reports.list(scope, status)).map(reportJson);
}

export async function get(actor, id) {
  const report = await reports.findById(v.positiveInt(id, 'report id'));
  if (!report || !actor.canAccessReport(reportRef(report))) throw new NotFoundError('Report not found.');
  return reportJson(report);
}

export async function updateStatus(actor, id, body) {
  const reportId = v.positiveInt(id, 'report id');
  const status = normalizeReportStatus(body.status);
  if (!status) throw new ValidationError('Choose a valid report status.');

  await withTransaction(async (tx) => {
    const report = await reports.lock(tx, reportId);
    if (!report) throw new NotFoundError('Report not found.');
    await reports.updateStatus(tx, reportId, status);
    if (body.remarks) await reports.appendRemarks(tx, reportId, v.text(body.remarks, { max: 2000 }));
    await activity.log(tx, {
      actorId: actor.id,
      entityType: 'REPORT',
      entityId: reportId,
      action: status,
      details: `Report ${report.report_code} marked ${REPORT_STATUS_LABELS[status]}`,
    });
    await activity.notify(tx, {
      userId: report.client_id,
      type: 'REPORT',
      message: `Your report ${report.report_code} is now marked ${REPORT_STATUS_LABELS[status]}.`,
    });
  });
  return reportJson(await reports.findById(reportId));
}
