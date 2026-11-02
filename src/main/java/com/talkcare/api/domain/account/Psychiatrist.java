package com.talkcare.api.domain.account;

import com.talkcare.api.domain.Role;
import com.talkcare.api.domain.ServiceType;
import java.time.LocalDate;
import java.util.Set;

/** Handles ThoMatt (mental health) consultations. */
public final class Psychiatrist extends Professional {

    private final String prcLicenseNo;
    private final LocalDate licenseExpiry;
    private final String clinicName;
    private final boolean acceptsMedicationManagement;

    public Psychiatrist(Identity identity, Set<String> permissions, Details details,
                        String prcLicenseNo, LocalDate licenseExpiry, String clinicName,
                        boolean acceptsMedicationManagement) {
        super(identity, permissions, details);
        this.prcLicenseNo = prcLicenseNo;
        this.licenseExpiry = licenseExpiry;
        this.clinicName = clinicName;
        this.acceptsMedicationManagement = acceptsMedicationManagement;
    }

    @Override
    public Role role() {
        return Role.PSYCHIATRIST;
    }

    @Override
    public ServiceType serviceType() {
        return ServiceType.THOMATT;
    }

    @Override
    protected String defaultTitle() {
        return "Dr.";
    }

    @Override
    public String dashboardView() {
        return "professional-dashboard";
    }

    public String prcLicenseNo() {
        return prcLicenseNo;
    }

    public LocalDate licenseExpiry() {
        return licenseExpiry;
    }

    public String clinicName() {
        return clinicName;
    }

    public boolean acceptsMedicationManagement() {
        return acceptsMedicationManagement;
    }
}
