package com.talkcare.api.domain.account;

import com.talkcare.api.domain.ServiceType;
import com.talkcare.api.domain.chat.Conversation;
import com.talkcare.api.domain.report.Report;
import java.util.Set;

/** A licensed professional; sees the conversations and reports assigned to them. */
public abstract sealed class Professional extends Account permits Attorney, Psychiatrist {

    private final Details details;

    protected Professional(Identity identity, Set<String> permissions, Details details) {
        super(identity, permissions);
        this.details = details;
    }

    /** The service this kind of professional handles. */
    public abstract ServiceType serviceType();

    /** Title used when {@code professionals.display_title} is empty. */
    protected abstract String defaultTitle();

    @Override
    public final boolean canAccessConversation(Conversation conversation) {
        return conversation.professionalId() == id();
    }

    @Override
    public final boolean canAccessReport(Report report) {
        return report.assignedProfessionalId() != null && report.assignedProfessionalId() == id();
    }

    public final String displayTitle() {
        return details.displayTitle() != null ? details.displayTitle() : defaultTitle();
    }

    public final String bio() {
        return details.bio();
    }

    public final Integer yearsOfExperience() {
        return details.yearsOfExperience();
    }

    public final boolean isAcceptingClients() {
        return details.acceptingClients();
    }

    /** Columns of {@code professionals}. */
    public record Details(String displayTitle, String bio, Integer yearsOfExperience, boolean acceptingClients) {
    }
}
