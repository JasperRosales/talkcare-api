package com.talkcare.api.service;

import com.talkcare.api.config.TalkCareProperties;
import com.talkcare.api.domain.Permission;
import com.talkcare.api.domain.UserStatus;
import com.talkcare.api.domain.activity.ActivityEntry;
import com.talkcare.api.domain.activity.Notification;
import com.talkcare.api.domain.account.Account;
import com.talkcare.api.domain.report.Report;
import com.talkcare.api.domain.report.ReportStatus;
import com.talkcare.api.domain.verification.PendingVerification;
import com.talkcare.api.domain.verification.VerificationStatus;
import com.talkcare.api.error.ConflictException;
import com.talkcare.api.error.ForbiddenException;
import com.talkcare.api.error.ValidationException;
import com.talkcare.api.repository.AccountRepository;
import com.talkcare.api.repository.ActivityRepository;
import com.talkcare.api.repository.AppointmentRepository;
import com.talkcare.api.repository.NotificationRepository;
import com.talkcare.api.repository.ReportRepository;
import com.talkcare.api.repository.VerificationRepository;
import com.talkcare.api.web.dto.ProcessRequestRequest;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin dashboard: metrics, user list, review queue, activity log, schedule. */
@Service
public class AdminService {

    private static final int USER_LIST_LIMIT = 50;
    private static final int ACTIVITY_LIMIT = 12;
    private static final Set<String> VERIFICATION_ACTIONS = Set.of("APPROVE", "REJECT");
    private static final Set<String> REPORT_ACTIONS = Set.of("IN_PROGRESS", "RESOLVED", "ADD_REMARKS");

    private final AccountRepository accounts;
    private final VerificationRepository verifications;
    private final ReportRepository reports;
    private final AppointmentRepository appointments;
    private final ActivityRepository activity;
    private final NotificationRepository notifications;
    private final ZoneId timezone;

    public AdminService(AccountRepository accounts, VerificationRepository verifications, ReportRepository reports,
                        AppointmentRepository appointments, ActivityRepository activity,
                        NotificationRepository notifications, TalkCareProperties properties) {
        this.accounts = accounts;
        this.verifications = verifications;
        this.reports = reports;
        this.appointments = appointments;
        this.activity = activity;
        this.notifications = notifications;
        this.timezone = properties.timezone();
    }

    public record Metrics(int totalUsers, int pendingApprovals, int sessionsToday) {
    }

    public record PendingRequests(List<PendingVerification> verifications, List<Report> reports) {
    }

    public record Schedule(int year, int month, String monthName, List<Integer> days) {
    }

    @Transactional(readOnly = true)
    public Metrics metrics() {
        int pending = verifications.countPending() + reports.countByStatus(ReportStatus.UNDER_REVIEW);
        return new Metrics(accounts.countAll(), pending, appointments.countToday(timezone));
    }

    public List<Account> users() {
        return accounts.list(USER_LIST_LIMIT);
    }

    /** Pending identity verifications and under-review reports the actor may act on. */
    @Transactional(readOnly = true)
    public PendingRequests pendingRequests(Account actor) {
        return new PendingRequests(
                actor.can(Permission.VERIFICATIONS_REVIEW) ? verifications.listPending() : List.of(),
                actor.can(Permission.REPORTS_UPDATE_STATUS) ? reports.listByStatus(ReportStatus.UNDER_REVIEW) : List.of());
    }

    @Transactional
    public void processRequest(Account actor, ProcessRequestRequest request) {
        int id = InputRules.positiveId(request.id(), "request id");
        String action = request.action() == null ? "" : request.action();
        if ("VERIFICATION".equals(request.type())) {
            if (!actor.can(Permission.VERIFICATIONS_REVIEW)) {
                throw new ForbiddenException();
            }
            if (!VERIFICATION_ACTIONS.contains(action)) {
                throw new ValidationException("Invalid verification action.");
            }
            if (action.equals("REJECT") && request.reason() == null) {
                throw new ValidationException("A rejection reason is required.");
            }
            reviewVerification(actor, id, action.equals("APPROVE"), request.reason());
        } else if ("REPORT".equals(request.type())) {
            if (!actor.can(Permission.REPORTS_UPDATE_STATUS)) {
                throw new ForbiddenException();
            }
            if (!REPORT_ACTIONS.contains(action)) {
                throw new ValidationException("Invalid report action.");
            }
            if (action.equals("ADD_REMARKS") && request.remarks() == null) {
                throw new ValidationException("Please enter remarks before saving.");
            }
            reviewReport(actor, id, action, request.remarks());
        } else {
            throw new ValidationException("Unknown request type.");
        }
    }

    public List<ActivityEntry> recentActivity() {
        return activity.recent(ACTIVITY_LIMIT);
    }

    /** Days with appointments in a month (default: the current month in the app time zone). */
    public Schedule schedule(String yearParam, String monthParam) {
        LocalDate today = LocalDate.now(timezone);
        int year = yearParam == null || yearParam.isEmpty() ? today.getYear() : InputRules.positiveId(yearParam, "year");
        int month = monthParam == null || monthParam.isEmpty() ? today.getMonthValue() : InputRules.positiveId(monthParam, "month");
        if (month > 12) {
            throw new ValidationException("Invalid month.");
        }
        String monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.US);
        return new Schedule(year, month, monthName, appointments.daysWithAppointments(year, month, timezone));
    }

    private void reviewVerification(Account actor, int id, boolean approved, String reason) {
        VerificationRepository.LockedVerification request = verifications.lockPending(id)
                .orElseThrow(() -> new ConflictException("This verification request is no longer pending."));
        verifications.review(id, approved ? VerificationStatus.APPROVED : VerificationStatus.REJECTED, actor.id(),
                approved ? null : reason);
        // Rejected users go back to ACTIVE so they can resubmit; users.status has no REJECTED value.
        accounts.setStatus(request.userId(), approved ? UserStatus.VERIFIED : UserStatus.ACTIVE);
        if (approved) {
            accounts.startAcceptingClients(request.userId());
        }
        String subject = request.fullName() == null || request.fullName().isEmpty() ? "User" : request.fullName();
        activity.log(new ActivityRepository.NewActivity(actor.id(), "VERIFICATION", id, approved ? "APPROVE" : "REJECT",
                approved ? "Verification approved for " + subject
                        : "Verification rejected for " + subject + ". Reason: " + reason));
        notifications.create(request.userId(), Notification.VERIFICATION,
                approved ? "Your account verification has been approved."
                        : "Your account verification was rejected: " + reason);
    }

    private void reviewReport(Account actor, int id, String action, String remarks) {
        ReportRepository.LockedReport report = reports.lock(id, ReportStatus.UNDER_REVIEW)
                .orElseThrow(() -> new ConflictException("This report is no longer pending."));
        String details;
        String notification;
        if (action.equals("ADD_REMARKS")) {
            reports.appendRemarks(id, remarks);
            details = "Remarks added to " + report.reportCode() + ". Remarks: " + remarks;
            notification = "A TalkCare admin added remarks to your report " + report.reportCode() + ".";
        } else {
            ReportStatus status = ReportStatus.valueOf(action);
            reports.updateStatus(id, status);
            details = "Report " + report.reportCode() + " marked " + status.label();
            notification = "Your report " + report.reportCode() + " is now marked " + status.label() + ".";
        }
        activity.log(new ActivityRepository.NewActivity(actor.id(), "REPORT", id, action, details));
        notifications.create(report.clientId(), Notification.REPORT, notification);
    }
}
