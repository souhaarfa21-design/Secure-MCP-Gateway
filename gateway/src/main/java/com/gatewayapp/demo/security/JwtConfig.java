package com.gatewayapp.demo.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * The gateway signs tokens (in /token) and checks them (on every /mcp call) with ONE secret key.
 *
 * HS256 = "HMAC with SHA-256": the signature is a fingerprint of the token made with the secret.
 * If anyone changes even one letter of the token, the fingerprint no longer matches → 401.
 * Only someone who knows JWT_SECRET (only the gateway) can make a valid token.
 */
@Configuration
public class JwtConfig {

    /** Written into every token ("iss") and checked when a token comes back. */
    public static final String ISSUER = "secure-mcp-gateway";

    private final SecretKey key;

    public JwtConfig(@Value("${app.jwt.secret}") String secret) {
        if (secret.length() < 32) {
            // HS256 needs at least 256 bits (32 bytes) of key, otherwise it is easy to guess
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters long");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** Makes and signs tokens (used by TokenController). */
    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    /**
     * Checks tokens on every request: signature, expiry ("exp") and issuer ("iss").
     * A missing, expired or tampered token is rejected with 401 Unauthorized.
     */
    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER)));   // default = expiry check
        return decoder;
    }
}
