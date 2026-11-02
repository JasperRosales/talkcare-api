import { Permission, Role, ServiceType } from './constants.js';

/**
 * Account hierarchy (plan.md Phase 1) mirroring the class-table inheritance in schema.md:
 *
 *   Account (abstract)            -> users + user_profiles
 *   ├── Client                    -> clients
 *   ├── Administrator             -> admins
 *   └── Professional (abstract)   -> professionals
 *       ├── Attorney              -> attorneys
 *       └── Psychiatrist          -> psychiatrists
 *
 * Authorization is permission-based (`can`) plus polymorphic resource scoping (`canAccess*`),
 * so endpoints never compare role strings.
 */
export class Account {
  #id;
  #username;
  #email;
  #status;
  #permissions;

  constructor(row, permissions) {
    if (new.target === Account) throw new TypeError('Account is abstract.');
    this.#id = row.id;
    this.#username = row.username;
    this.#email = row.email;
    this.#status = row.status;
    this.#permissions = new Set(permissions);
    this.profile = Object.freeze({
      fullName: row.full_name ?? '',
      contactNo: row.contact_no ?? '',
      address: row.address ?? '',
      birthDate: row.birth_date ?? null,
      profilePictureUrl: row.profile_picture_url ?? '',
    });
    this.verification = row.verification_status
      ? Object.freeze({
          status: row.verification_status,
          idType: row.verification_id_type ?? '',
          idNumber: row.verification_id_number ?? '',
          rejectionReason: row.verification_rejection_reason ?? null,
          submittedAt: row.verification_submitted_at ?? null,
        })
      : null;
    this.createdAt = row.created_at ?? null;
  }

  get id() { return this.#id; }
  get username() { return this.#username; }
  get email() { return this.#email; }
  get status() { return this.#status; }
  get permissions() { return [...this.#permissions].sort(); }

  /** @abstract */ get role() { throw new Error('not implemented'); }
  /** @abstract */ get dashboardView() { throw new Error('not implemented'); }

  get roleDisplayName() { return this.role.charAt(0) + this.role.slice(1).toLowerCase(); }
  get isActive() { return !['SUSPENDED', 'DEACTIVATED'].includes(this.#status); }
  get displayName() { return this.profile.fullName || this.#username; }

  can(permission) { return this.#permissions.has(permission); }

  /** @abstract */ canAccessConversation(_conversation) { return false; }
  /** @abstract */ canAccessReport(_report) { return false; }

  /** Subtype-specific fields added to the JSON representation. */
  subtypeJson() { return {}; }

  toJSON() {
    return {
      id: this.id,
      username: this.username,
      email: this.email,
      fullName: this.profile.fullName,
      contactNo: this.profile.contactNo,
      address: this.profile.address,
      profilePictureUrl: this.profile.profilePictureUrl,
      role: this.role,
      roleDisplayName: this.roleDisplayName,
      status: this.status,
      dashboardView: this.dashboardView,
      permissions: this.permissions,
      idType: this.verification?.idType ?? '',
      idNumber: this.verification?.idNumber ?? '',
      verificationStatus: this.verification?.status ?? null,
      createdAt: this.createdAt,
      ...this.subtypeJson(),
    };
  }
}

export class Client extends Account {
  constructor(row, permissions) {
    super(row, permissions);
    this.preferredService = row.client_preferred_service ?? null;
    this.emergencyContactName = row.client_emergency_contact_name ?? '';
    this.emergencyContactNo = row.client_emergency_contact_no ?? '';
  }

  get role() { return Role.CLIENT; }
  get dashboardView() { return 'home'; }
  canAccessConversation(c) { return c.clientId === this.id; }
  canAccessReport(r) { return r.clientId === this.id; }

  subtypeJson() {
    return {
      preferredService: this.preferredService,
      emergencyContactName: this.emergencyContactName,
      emergencyContactNo: this.emergencyContactNo,
    };
  }
}

export class Professional extends Account {
  constructor(row, permissions) {
    super(row, permissions);
    if (new.target === Professional) throw new TypeError('Professional is abstract.');
    this.title = row.pro_display_title ?? this.defaultTitle;
    this.bio = row.pro_bio ?? '';
    this.yearsOfExperience = row.pro_years_of_experience ?? null;
    this.acceptingClients = row.pro_is_accepting_clients ?? true;
  }

  /** @abstract */ get serviceType() { throw new Error('not implemented'); }
  /** @abstract */ get defaultTitle() { throw new Error('not implemented'); }

  canAccessConversation(c) { return c.professionalId === this.id; }
  canAccessReport(r) { return r.assignedProfessionalId === this.id; }

  subtypeJson() {
    return {
      serviceType: this.serviceType,
      displayTitle: this.title,
      bio: this.bio,
      yearsOfExperience: this.yearsOfExperience,
      acceptingClients: this.acceptingClients,
    };
  }
}

export class Attorney extends Professional {
  constructor(row, permissions) {
    super(row, permissions);
    this.barRollNo = row.atty_bar_roll_no ?? '';
    this.ibpNo = row.atty_ibp_no ?? '';
    this.lawFirm = row.atty_law_firm ?? '';
    this.jurisdiction = row.atty_jurisdiction ?? '';
  }

  get role() { return Role.ATTORNEY; }
  get serviceType() { return ServiceType.JUSTICE_CARE; }
  get defaultTitle() { return 'Atty.'; }
  get dashboardView() { return 'case-handler-dashboard'; }

  subtypeJson() {
    return {
      ...super.subtypeJson(),
      barRollNo: this.barRollNo,
      ibpNo: this.ibpNo,
      lawFirm: this.lawFirm,
      jurisdiction: this.jurisdiction,
    };
  }
}

export class Psychiatrist extends Professional {
  constructor(row, permissions) {
    super(row, permissions);
    this.prcLicenseNo = row.psy_prc_license_no ?? '';
    this.licenseExpiry = row.psy_license_expiry ?? null;
    this.clinicName = row.psy_clinic_name ?? '';
    this.acceptsMedicationManagement = row.psy_accepts_medication_management ?? true;
  }

  get role() { return Role.PSYCHIATRIST; }
  get serviceType() { return ServiceType.THOMATT; }
  get defaultTitle() { return 'Dr.'; }
  get dashboardView() { return 'professional-dashboard'; }

  subtypeJson() {
    return {
      ...super.subtypeJson(),
      prcLicenseNo: this.prcLicenseNo,
      licenseExpiry: this.licenseExpiry,
      clinicName: this.clinicName,
      acceptsMedicationManagement: this.acceptsMedicationManagement,
    };
  }
}

export class Administrator extends Account {
  constructor(row, permissions) {
    super(row, permissions);
    this.jobTitle = row.admin_job_title ?? '';
    this.department = row.admin_department ?? '';
    this.isSuperAdmin = row.admin_is_super_admin ?? false;
  }

  get role() { return Role.ADMIN; }
  get roleDisplayName() { return 'Administrator'; }
  get dashboardView() { return 'admin'; }
  canAccessConversation() { return this.can(Permission.CONVERSATIONS_READ_ASSIGNED); }
  canAccessReport() { return this.can(Permission.REPORTS_READ_ALL); }

  subtypeJson() {
    return { jobTitle: this.jobTitle, department: this.department, isSuperAdmin: this.isSuperAdmin };
  }
}

const SUBTYPES = new Map([
  [Role.CLIENT, Client],
  [Role.ATTORNEY, Attorney],
  [Role.PSYCHIATRIST, Psychiatrist],
  [Role.ADMIN, Administrator],
]);

/** Picks the subtype from `roles.code`; the DB triggers guarantee the subtype row matches. */
export const AccountFactory = Object.freeze({
  fromRow(row, permissions) {
    const Subtype = SUBTYPES.get(row.role_code);
    if (!Subtype) throw new Error(`Unknown role code "${row.role_code}" for user ${row.id}.`);
    return new Subtype(row, permissions);
  },
});
