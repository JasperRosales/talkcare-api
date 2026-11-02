package com.talkcare.api.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.talkcare.api.config.TalkCareProperties;
import com.talkcare.api.domain.account.Account;
import com.talkcare.api.error.UnauthenticatedException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/**
 * Stateless session tokens: HS256 JWTs with {@code sub} = user id and a {@code role} claim, the
 * same format the Express API issued, so existing sessions stay valid across the switch.
 */
@Service
public class SessionTokenService {

    public static final String SESSION_EXPIRED = "Your session has expired. Please sign in again.";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Duration lifetime;

    public SessionTokenService(TalkCareProperties properties) {
        SecretKey key = new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        nimbusDecoder.setJwtValidator(new JwtTimestampValidator(Duration.ZERO));
        this.decoder = nimbusDecoder;
        this.lifetime = properties.jwt().expiresIn();
    }

    public String issue(Account account) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(Integer.toString(account.id()))
                .claim("role", account.role().name())
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Returns the user id of a valid, unexpired token. */
    public int verify(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            return Integer.parseInt(jwt.getSubject());
        } catch (JwtException | NumberFormatException ex) {
            throw new UnauthenticatedException(SESSION_EXPIRED);
        }
    }
}
