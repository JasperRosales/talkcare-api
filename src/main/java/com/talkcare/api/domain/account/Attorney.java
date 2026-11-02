package com.talkcare.api.domain.account;

import com.talkcare.api.domain.Role;
import com.talkcare.api.domain.ServiceType;
import java.util.Set;

/** Handles JusticeCare (legal) cases. */
public final class Attorney extends Professional {

    private final String barRollNo;
    private final String ibpNo;
    private final String lawFirm;
    private final String jurisdiction;

    public Attorney(Identity identity, Set<String> permissions, Details details,
                    String barRollNo, String ibpNo, String lawFirm, String jurisdiction) {
        super(identity, permissions, details);
        this.barRollNo = barRollNo;
        this.ibpNo = ibpNo;
        this.lawFirm = lawFirm;
        this.jurisdiction = jurisdiction;
    }

    @Override
    public Role role() {
        return Role.ATTORNEY;
    }

    @Override
    public ServiceType serviceType() {
        return ServiceType.JUSTICE_CARE;
    }

    @Override
    protected String defaultTitle() {
        return "Atty.";
    }

    @Override
    public String dashboardView() {
        return "case-handler-dashboard";
    }

    public String barRollNo() {
        return barRollNo;
    }

    public String ibpNo() {
        return ibpNo;
    }

    public String lawFirm() {
        return lawFirm;
    }

    public String jurisdiction() {
        return jurisdiction;
    }
}
