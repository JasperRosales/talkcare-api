package com.talkcare.api.service;

import com.talkcare.api.config.TalkCareProperties;
import com.talkcare.api.domain.Role;
import com.talkcare.api.domain.account.Account;
import com.talkcare.api.error.ServiceUnavailableException;
import com.talkcare.api.error.UnauthenticatedException;
import com.talkcare.api.error.ValidationException;
import com.talkcare.api.mail.EmailService;
import com.talkcare.api.repository.AccountRepository;
import com.talkcare.api.repository.PasswordResetRepository;
import com.talkcare.api.security.SessionTokenService;
import com.talkcare.api.web.dto.ForgotPasswordRequest;
import com.talkcare.api.web.dto.LoginRequest;
import com.talkcare.api.web.dto.RegisterProfessionalRequest;
import com.talkcare.api.web.dto.RegisterRequest;
import com.talkcare.api.web.dto.ResetPasswordRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Sign-in, registration and password recovery. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int RESET_TTL_MINUTES = 30;

    private final AccountRepository accounts;
    private final PasswordResetRepository resets;
    private final PasswordEncoder passwords;
    private final SessionTokenService tokens;
    private final EmailService email;
    private final TransactionTemplate transaction;
    private final String resetUrl;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AccountRepository accounts, PasswordResetRepository resets, PasswordEncoder passwords,
                       SessionTokenService tokens, EmailService email, TransactionTemplate transaction,
                       TalkCareProperties properties) {
        this.accounts = accounts;
        this.resets = resets;
        this.passwords = passwords;
        this.tokens = tokens;
        this.email = email;
        this.transaction = transaction;
        this.resetUrl = properties.resetUrl();
    }

    /** A signed-in account and its bearer token. */
    public record Session(Account account, String token) {
    }

    public Session login(LoginRequest request) {
        String loginId = request.loginId();
        String password = request.password();
        if (loginId == null || password == null || password.isEmpty()) {
            throw new ValidationException("Enter your username or email and password.");
        }
        for (AccountRepository.Credentials candidate : accounts.findCredentialsByLoginId(loginId)) {
            if (matches(password, candidate.passwordHash())) {
                Account account = accounts.findById(candidate.id()).orElseThrow();
                if (!account.isActive()) {
                    throw new UnauthenticatedException("This account is suspended or deactivated.");
                }
                if (request.role() != null && request.role() != account.role()) {
                    throw new UnauthenticatedException("This is not " + article(portalName(request.role()))
                            + " account. Use the " + portalName(account.role()) + " sign-in page.");
                }
                accounts.touchLastLogin(account.id());
                return startSession(account);
            }
        }
        throw new UnauthenticatedException("Invalid username, email, full name, or password.");
    }

    /** Creates a CLIENT account and signs it in. */
    public Session register(RegisterRequest request) {
        String passwordHash = passwords.encode(InputRules.newPassword(request.password()));
        Integer id = transaction.execute(status -> accounts.createClient(new AccountRepository.NewClient(
                request.username(), request.email(), passwordHash,
                request.fullName(), request.contactNo(), request.address())));
        return startSession(accounts.findById(id).orElseThrow());
    }

    /**
     * Creates an ATTORNEY or PSYCHIATRIST account and signs it in. It stays PENDING_VERIFICATION,
     * without new clients, until an administrator approves its identity verification.
     */
    public Session registerProfessional(Role role, RegisterProfessionalRequest request) {
        String passwordHash = passwords.encode(InputRules.newPassword(request.password()));
        Integer id = transaction.execute(status -> accounts.createProfessional(new AccountRepository.NewProfessional(
                role, request.username(), request.email(), passwordHash, request.fullName(),
                request.contactNo(), request.address(), request.licenseNo(), request.affiliation())));
        return startSession(accounts.findById(id).orElseThrow());
    }

    private static String portalName(Role role) {
        String code = role.name();
        return code.charAt(0) + code.substring(1).toLowerCase();
    }

    private static String article(String word) {
        return ("AEIOU".indexOf(Character.toUpperCase(word.charAt(0))) >= 0 ? "an " : "a ") + word;
    }

    /** Emails a one-time reset link. The response never reveals whether the email exists. */
    public String requestPasswordReset(ForgotPasswordRequest request) {
        if (!email.isConfigured()) {
            throw new ServiceUnavailableException("Password recovery email is not configured. Contact the administrator.");
        }
        if (request.email() != null) {
            accounts.findByEmail(request.email()).ifPresent(this::sendResetLink);
        }
        return "If an account exists for that email, a password reset link has been sent.";
    }

    public String resetPassword(ResetPasswordRequest request) {
        String passwordHash = passwords.encode(InputRules.newPassword(request.newPassword()));
        String token = request.token();
        Boolean updated = transaction.execute(status -> {
            if (token == null) {
                return false;
            }
            String tokenHash = sha256(token);
            return resets.lockUsable(tokenHash).map(userId -> {
                accounts.updateCredentials(userId, null, null, passwordHash);
                resets.markUsed(tokenHash);
                return true;
            }).orElse(false);
        });
        if (!Boolean.TRUE.equals(updated)) {
            throw new ValidationException("This reset link is invalid, expired, or already used. Request a new link.");
        }
        return "Password updated. You can now log in.";
    }

    private void sendResetLink(AccountRepository.EmailOwner user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = sha256(token);
        transaction.executeWithoutResult(status -> resets.replaceForUser(user.id(), tokenHash, RESET_TTL_MINUTES));
        try {
            email.send(user.email(), "Reset your TalkCare password",
                    "A password reset was requested for your TalkCare account.\n\n"
                            + "Use this one-time link within " + RESET_TTL_MINUTES + " minutes:\n"
                            + resetUrl + "/?resetToken=" + token + "\n\n"
                            + "If you did not request this, you can ignore this email.");
        } catch (RuntimeException ex) {
            resets.remove(tokenHash);
            log.error("Could not send password reset email: {}", ex.getMessage());
            throw new ServiceUnavailableException(
                    "Could not send the password reset email. Check the SMTP configuration and try again.");
        }
    }

    private Session startSession(Account account) {
        return new Session(account, tokens.issue(account));
    }

    /** bcrypt rejects passwords over 72 bytes; such a password cannot match any stored hash. */
    private boolean matches(String password, String hash) {
        try {
            return passwords.matches(password, hash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
