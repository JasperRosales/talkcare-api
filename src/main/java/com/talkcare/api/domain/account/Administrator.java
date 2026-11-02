package com.talkcare.api.domain.account;

import com.talkcare.api.domain.Permission;
import com.talkcare.api.domain.Role;
import com.talkcare.api.domain.chat.Conversation;
import com.talkcare.api.domain.report.Report;
import java.util.Set;

/** Platform administrator; resource access follows from granted permissions. */
public final class Administrator extends Account {

    private final String jobTitle;
    private final String department;
    private final boolean superAdmin;

    public Administrator(Identity identity, Set<String> permissions, String jobTitle, String department, boolean superAdmin) {
        super(identity, permissions);
        this.jobTitle = jobTitle;
        this.department = department;
        this.superAdmin = superAdmin;
    }

    @Override
    public Role role() {
        return Role.ADMIN;
    }

    @Override
    public String roleDisplayName() {
        return "Administrator";
    }

    @Override
    public String dashboardView() {
        return "admin";
    }

    @Override
    public boolean canAccessConversation(Conversation conversation) {
        return can(Permission.CONVERSATIONS_READ_ASSIGNED);
    }

    @Override
    public boolean canAccessReport(Report report) {
        return can(Permission.REPORTS_READ_ALL);
    }

    public String jobTitle() {
        return jobTitle;
    }

    public String department() {
        return department;
    }

    public boolean isSuperAdmin() {
        return superAdmin;
    }
}
