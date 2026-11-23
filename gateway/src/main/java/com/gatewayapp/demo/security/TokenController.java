package com.gatewayapp.demo.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * POST /token  {"username":"alice", "password":"...", "agent":"support-agent"}
 * →            {"access_token":"eyJ...", "token_type":"Bearer", "expires_in":900}
 *
 * The token says WHO is calling (sub), with WHICH role (role), through WHICH agent (agent).
 * The caller then sends it on every /mcp request:  Authorization: Bearer eyJ...
 *
 * This is a small demo login, not a real identity provider: the users and their roles are
 * listed in application.properties and share one demo password from .env.
 */
@RestController
public class TokenController {

    public record TokenRequest(
            @NotBlank @Size(max = 60) String username,
            @NotBlank String password,
            @NotBlank @Size(max = 60) String agent) {}

    public record TokenResponse(String access_token, String token_type, long expires_in) {}

    private final JwtEncoder encoder;
    private final Map<String, String> userRoles;
    private final byte[] demoPassword;
    private final Duration lifetime;

    public TokenController(JwtEncoder encoder,
                           AuthProperties props) {
        this.encoder = encoder;
        this.userRoles = props.users();
        this.demoPassword = props.demoPassword().getBytes(StandardCharsets.UTF_8);
        this.lifetime = props.tokenLifetime();
    }

    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        String role = userRoles.get(request.username());
        // constant-time compare, so the answer time doesn't leak how much of the password was right
        boolean passwordOk = MessageDigest.isEqual(
                request.password().getBytes(StandardCharsets.UTF_8), demoPassword);
        if (role == null || !passwordOk) {
            // same answer for "unknown user" and "wrong password": don't tell an attacker which one
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad username or password");
        }

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(request.username())
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .claim("role", role)
                .claim("agent", request.agent())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", lifetime.toSeconds());
    }
}
