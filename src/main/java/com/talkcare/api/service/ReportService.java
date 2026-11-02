package com.talkcare.api.service;

import com.talkcare.api.domain.Permission;
import com.talkcare.api.domain.ServiceType;
import com.talkcare.api.domain.activity.Notification;
import com.talkcare.api.domain.account.Account;
import com.talkcare.api.domain.account.Client;
import com.talkcare.api.domain.account.Professional;
import com.talkcare.api.domain.lookup.ConcernType;
import com.talkcare.api.domain.lookup.ServiceTypeInfo;
import com.talkcare.api.domain.report.Report;
import com.talkcare.api.domain.report.ReportStatus;
import com.talkcare.api.error.ForbiddenException;
import com.talkcare.api.error.NotFoundException;
import com.talkcare.api.error.ValidationException;
import com.talkcare.api.repository.ActivityRepository;
import com.talkcare.api.repository.LookupRepository;
import com.talkcare.api.repository.NotificationRepository;
import com.talkcare.api.repository.ReportRepository;
import com.talkcare.api.web.dto.CreateReportRequest;
import com.talkcare.api.web.dto.UpdateReportStatusRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Concern reports submitted by clients. */
@Service
public class ReportService {

    private final ReportRepository reports;
    private final LookupRepository lookups;
    private final ActivityRepository activity;
    private final NotificationRepository notifications;

    public ReportService(ReportRepository reports, LookupRepository lookups, ActivityRepository activity,
                         NotificationRepository notifications) {
        this.reports = reports;
        this.lookups = lookups;
        this.activity = activity;
        this.notifications = notifications;
    }

    /** The service comes from the concern type; untied concerns use the requested one, else JusticeCare. */
    @Transactional
    public Report submit(Account actor, CreateReportRequest request) {
        // reports.client_id must reference a CLIENT (enforced by the validate_report trigger).
        if (!(actor instanceof Client)) {
            throw new ForbiddenException("Only client accounts can submit a concern report.");
        }
        ConcernType concern = lookups.findConcernType(request.concernType())
                .orElseThrow(() -> new ValidationException("\"" + request.concernType() + "\" is not a recognised concern type."));
        ServiceType serviceCode = concern.serviceType() != null ? concern.serviceType()
                : ServiceType.fromCode(request.serviceType()).orElse(ServiceType.JUSTICE_CARE);
        ServiceTypeInfo service = lookups.findServiceType(serviceCode).orElseThrow();

        int id = reports.create(new ReportRepository.NewReport(
                actor.id(), service.id(), concern.id(), request.involvedWitness(), request.concernDetails(),
                new Report.Reporter(request.fullName(), request.email(), request.contactNo(), request.address())));
        activity.log(new ActivityRepository.NewActivity(actor.id(), "REPORT", id, "SUBMITTED",
                "Report submitted by " + actor.displayName() + " (" + concern.name() + ")"));
        return reports.findById(id).orElseThrow();
    }

    /**
     * Reports visible to the actor: all (admin), assigned (professional) or own (client).
     * {@code filter} is a status code/label, or "all"/empty for every status.
     */
    @Transactional(readOnly = true)
    public List<Report> list(Account actor, String filter) {
        ReportStatus status = null;
        if (filter != null && !filter.isEmpty() && !filter.equalsIgnoreCase("all")) {
            status = ReportStatus.parse(filter).orElseThrow(() -> new ValidationException("Unknown report status filter."));
        }
        return reports.list(scopeFor(actor), status);
    }

    @Transactional(readOnly = true)
    public Report get(Account actor, String id) {
        return reports.findById(InputRules.positiveId(id, "report id"))
                .filter(actor::canAccessReport)
                .orElseThrow(() -> new NotFoundException("Report not found."));
    }

    @Transactional
    public Report updateStatus(Account actor, String id, UpdateReportStatusRequest request) {
        int reportId = InputRules.positiveId(id, "report id");
        ReportStatus status = ReportStatus.parse(request.status())
                .orElseThrow(() -> new ValidationException("Choose a valid report status."));
        ReportRepository.LockedReport report = reports.lock(reportId, null)
                .orElseThrow(() -> new NotFoundException("Report not found."));
        reports.updateStatus(reportId, status);
        if (request.remarks() != null) {
            reports.appendRemarks(reportId, request.remarks());
        }
        activity.log(new ActivityRepository.NewActivity(actor.id(), "REPORT", reportId, status.name(),
                "Report " + report.reportCode() + " marked " + status.label()));
        notifications.create(report.clientId(), Notification.REPORT,
                "Your report " + report.reportCode() + " is now marked " + status.label() + ".");
        return reports.findById(reportId).orElseThrow();
    }

    private static ReportRepository.Scope scopeFor(Account actor) {
        if (actor.can(Permission.REPORTS_READ_ALL)) {
            return new ReportRepository.Scope.All();
        }
        if (actor instanceof Professional && actor.can(Permission.REPORTS_READ_ASSIGNED)) {
            return new ReportRepository.Scope.AssignedTo(actor.id());
        }
        if (actor.can(Permission.REPORTS_READ_OWN)) {
            return new ReportRepository.Scope.OwnedBy(actor.id());
        }
        throw new ForbiddenException();
    }
}
