package com.talkcare.api.domain.account;

import com.talkcare.api.domain.Role;
import com.talkcare.api.domain.ServiceType;
import com.talkcare.api.domain.chat.Conversation;
import com.talkcare.api.domain.report.Report;
import java.util.Set;

/** A person seeking help; sees only their own conversations and reports. */
public final class Client extends Account {

    private final ServiceType preferredService;
    private final String emergencyContactName;
    private final String emergencyContactNo;

    public Client(Identity identity, Set<String> permissions, ServiceType preferredService,
                  String emergencyContactName, String emergencyContactNo) {
        super(identity, permissions);
        this.preferredService = preferredService;
        this.emergencyContactName = emergencyContactName;
        this.emergencyContactNo = emergencyContactNo;
    }

    @Override
    public Role role() {
        return Role.CLIENT;
    }

    @Override
    public String dashboardView() {
        return "dashboard";
    }

    @Override
    public boolean canAccessConversation(Conversation conversation) {
        return conversation.clientId() == id();
    }

    @Override
    public boolean canAccessReport(Report report) {
        return report.clientId() == id();
    }

    public ServiceType preferredService() {
        return preferredService;
    }

    public String emergencyContactName() {
        return emergencyContactName;
    }

    public String emergencyContactNo() {
        return emergencyContactNo;
    }
}
