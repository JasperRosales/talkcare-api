package com.talkcare.api.service;

import com.talkcare.api.domain.activity.Notification;
import com.talkcare.api.domain.account.Account;
import com.talkcare.api.repository.LookupRepository;
import com.talkcare.api.repository.NotificationRepository;
import com.talkcare.api.web.dto.LookupsResponse;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Small read-mostly features: database health, reference data, notifications. */
@Service
public class SupportService {

    private final JdbcClient jdbc;
    private final LookupRepository lookups;
    private final NotificationRepository notifications;

    public SupportService(JdbcClient jdbc, LookupRepository lookups, NotificationRepository notifications) {
        this.jdbc = jdbc;
        this.lookups = lookups;
        this.notifications = notifications;
    }

    /** Throws {@link DataAccessException} when the database is unreachable. */
    public void checkDatabase() {
        jdbc.sql("SELECT 1").query(Integer.class).single();
    }

    @Transactional(readOnly = true)
    public LookupsResponse lookups() {
        return LookupsResponse.of(lookups.listServiceTypes(), lookups.listConcernTypes(), lookups.listIdTypes());
    }

    public List<Notification> notifications(Account actor) {
        return notifications.findForUser(actor.id());
    }

    public void markNotificationsRead(Account actor) {
        notifications.markAllRead(actor.id());
    }
}
