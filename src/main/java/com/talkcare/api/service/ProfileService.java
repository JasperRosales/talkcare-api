package com.talkcare.api.service;

import com.talkcare.api.domain.account.Account;
import com.talkcare.api.error.ValidationException;
import com.talkcare.api.repository.AccountRepository;
import com.talkcare.api.storage.FileStorage;
import com.talkcare.api.storage.UploadPolicy;
import com.talkcare.api.web.dto.UpdateProfileRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** The signed-in user's own credentials, profile and photo. */
@Service
public class ProfileService {

    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final FileStorage storage;
    private final TransactionTemplate transaction;

    public ProfileService(AccountRepository accounts, PasswordEncoder passwords, FileStorage storage,
                          TransactionTemplate transaction) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.storage = storage;
        this.transaction = transaction;
    }

    public Account updateProfile(Account actor, UpdateProfileRequest request) {
        String newPassword = InputRules.optionalNewPassword(request.password());
        String passwordHash = newPassword == null ? null : passwords.encode(newPassword);
        transaction.executeWithoutResult(status -> {
            accounts.updateCredentials(actor.id(), request.username(), request.email(), passwordHash);
            accounts.updateProfile(actor.id(), request.fullName(), request.contactNo(), request.address(), null);
        });
        return accounts.findById(actor.id()).orElseThrow();
    }

    /** Stores the new photo and returns its URL; the previous upload is deleted. */
    public String uploadPhoto(Account actor, String image) {
        if (image == null || image.isEmpty()) {
            throw new ValidationException("Choose a photo to upload.");
        }
        FileStorage.StoredFile saved = storage.saveDataUrl(image, UploadPolicy.photo("profile_" + actor.id(), "profile photo"));
        String previous = actor.profile().profilePictureUrl();
        accounts.updateProfile(actor.id(), null, null, null, saved.url());
        if (previous != null && !previous.equals(saved.url()) && previous.contains("/uploads/")) {
            storage.remove(previous);
        }
        return saved.url();
    }
}
