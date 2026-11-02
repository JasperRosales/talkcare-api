package com.talkcare.api.security;

import com.talkcare.api.domain.account.Account;
import com.talkcare.api.error.UnauthenticatedException;
import com.talkcare.api.repository.AccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves {@code Authorization: Bearer <token>} (or {@code X-Session-Token}) to a fresh
 * {@link Account}, re-reading status and permissions on every request. The account becomes the
 * authentication principal and its permission codes become its authorities.
 *
 * <p>An invalid token does not fail the request here: public endpoints must keep working when
 * the desktop app sends a stale token. Protected endpoints are rejected by the entry point,
 * which reports why via {@link #FAILURE_ATTRIBUTE}.
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    public static final String FAILURE_ATTRIBUTE = SessionAuthenticationFilter.class.getName() + ".failure";
    private static final Pattern BEARER = Pattern.compile("^bearer\\s+", Pattern.CASE_INSENSITIVE);

    private final SessionTokenService tokens;
    private final AccountRepository accounts;

    public SessionAuthenticationFilter(SessionTokenService tokens, AccountRepository accounts) {
        this.tokens = tokens;
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = readToken(request);
        if (token != null) {
            try {
                Account account = authenticate(token);
                List<SimpleGrantedAuthority> authorities =
                        account.permissions().stream().map(SimpleGrantedAuthority::new).toList();
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(account, null, authorities));
            } catch (UnauthenticatedException ex) {
                request.setAttribute(FAILURE_ATTRIBUTE, ex);
            }
        }
        chain.doFilter(request, response);
    }

    private Account authenticate(String token) {
        int userId = tokens.verify(token);
        return accounts.findById(userId)
                .filter(Account::isActive)
                .orElseThrow(() -> new UnauthenticatedException(SessionTokenService.SESSION_EXPIRED));
    }

    private static String readToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && BEARER.matcher(header).find()) {
            String token = BEARER.matcher(header).replaceFirst("").trim();
            return token.isEmpty() ? null : token;
        }
        String sessionToken = request.getHeader("X-Session-Token");
        return StringUtils.hasText(sessionToken) ? sessionToken.trim() : null;
    }
}
