package com.talkcare.api.web;

import com.talkcare.api.domain.Permission;
import com.talkcare.api.domain.account.Account;
import com.talkcare.api.domain.UserStatus;
import com.talkcare.api.service.ProfileService;
import com.talkcare.api.service.SupportService;
import com.talkcare.api.service.VerificationService;
import com.talkcare.api.web.dto.AccountResponse;
import com.talkcare.api.web.dto.NotificationResponse;
import com.talkcare.api.web.dto.PhotoUploadRequest;
import com.talkcare.api.web.dto.SimpleResponses.Photo;
import com.talkcare.api.web.dto.SimpleResponses.Success;
import com.talkcare.api.web.dto.SimpleResponses.VerificationSubmitted;
import com.talkcare.api.web.dto.UpdateProfileRequest;
import com.talkcare.api.web.dto.VerificationRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own profile, notifications and identity verification. */
@RestController
@RequestMapping("/api")
public class ProfileController {

    private final ProfileService profiles;
    private final VerificationService verifications;
    private final SupportService support;

    public ProfileController(ProfileService profiles, VerificationService verifications, SupportService support) {
        this.profiles = profiles;
        this.verifications = verifications;
        this.support = support;
    }

    @PatchMapping("/profile")
    public AccountResponse updateProfile(@AuthenticationPrincipal Account actor, @Valid @RequestBody UpdateProfileRequest request) {
        return AccountResponse.from(profiles.updateProfile(actor, request));
    }

    @PostMapping("/profile/photo")
    public Photo uploadPhoto(@AuthenticationPrincipal Account actor, @RequestBody PhotoUploadRequest request) {
        return new Photo(profiles.uploadPhoto(actor, request.image()));
    }

    @GetMapping("/notifications")
    public List<NotificationResponse> notifications(@AuthenticationPrincipal Account actor) {
        return support.notifications(actor).stream().map(NotificationResponse::from).toList();
    }

    @PostMapping("/notifications/read")
    public Success markNotificationsRead(@AuthenticationPrincipal Account actor) {
        support.markNotificationsRead(actor);
        return Success.OK;
    }

    @PostMapping("/verifications")
    @PreAuthorize("hasAuthority('" + Permission.VERIFICATIONS_SUBMIT + "')")
    public VerificationSubmitted submitVerification(@AuthenticationPrincipal Account actor,
                                                    @Valid @RequestBody VerificationRequest request) {
        UserStatus status = verifications.submit(actor, request);
        return new VerificationSubmitted(true, status.name());
    }
}
