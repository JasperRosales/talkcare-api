package com.talkcare.api.service;

import com.talkcare.api.domain.UserStatus;
import com.talkcare.api.domain.account.Account;
import com.talkcare.api.domain.lookup.IdType;
import com.talkcare.api.error.ValidationException;
import com.talkcare.api.repository.AccountRepository;
import com.talkcare.api.repository.ActivityRepository;
import com.talkcare.api.repository.LookupRepository;
import com.talkcare.api.repository.VerificationRepository;
import com.talkcare.api.storage.FileStorage;
import com.talkcare.api.storage.UploadPolicy;
import com.talkcare.api.web.dto.VerificationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Identity verification submitted by a user for admin review. */
@Service
public class VerificationService {

    private final LookupRepository lookups;
    private final VerificationRepository verifications;
    private final AccountRepository accounts;
    private final ActivityRepository activity;
    private final FileStorage storage;
    private final TransactionTemplate transaction;

    public VerificationService(LookupRepository lookups, VerificationRepository verifications, AccountRepository accounts,
                               ActivityRepository activity, FileStorage storage, TransactionTemplate transaction) {
        this.lookups = lookups;
        this.verifications = verifications;
        this.accounts = accounts;
        this.activity = activity;
        this.storage = storage;
        this.transaction = transaction;
    }

    /**
     * Stores the ID image, then records the (replaced) pending verification, updates the email and
     * address, and marks the account PENDING_VERIFICATION. If the transaction fails the new image is
     * deleted; if it succeeds the replaced image is.
     */
    public UserStatus submit(Account actor, VerificationRequest request) {
        IdType idType = lookups.findIdType(request.idType())
                .orElseThrow(() -> new ValidationException("\"" + request.idType() + "\" is not an accepted ID type."));
        FileStorage.StoredFile saved = storage.saveDataUrl(request.idDocImage(), UploadPolicy.idDocument("id_" + actor.id()));

        VerificationRepository.Upserted result;
        try {
            result = transaction.execute(status -> {
                VerificationRepository.Upserted upserted = verifications.upsertPending(
                        new VerificationRepository.Submission(actor.id(), idType.id(), request.idNumber(), saved.url()));
                accounts.updateCredentials(actor.id(), null, request.email(), null);
                accounts.updateProfile(actor.id(), null, null, request.address(), null);
                accounts.setStatus(actor.id(), UserStatus.PENDING_VERIFICATION);
                activity.log(new ActivityRepository.NewActivity(actor.id(), "VERIFICATION", upserted.id(), "SUBMITTED",
                        "Identity verification submitted by " + actor.displayName()));
                return upserted;
            });
        } catch (RuntimeException ex) {
            storage.remove(saved.url());
            throw ex;
        }
        if (result != null && result.previousDocumentUrl() != null) {
            storage.remove(result.previousDocumentUrl());
        }
        return UserStatus.PENDING_VERIFICATION;
    }
}
