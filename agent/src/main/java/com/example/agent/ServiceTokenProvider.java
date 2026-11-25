package com.example.agent;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The agent app's OWN token (user "support-agent", role SERVICE).
 *
 * Why: when the agent starts, it connects to the gateway and asks "which tools do you have?".
 * No human is logged in at that moment, so there is no user token to forward. The agent then
 * logs in as itself, like a staff badge for the building's lobby.
 *
 * The token is kept and re-used, and fetched again shortly before it expires (15 min).
 */
@Component
public class ServiceTokenProvider {

    private record TokenResponse(String access_token, long expires_in) {}

    private final RestClient gateway;
    private final String username;
    private final String password;

    private String token;
    private Instant renewAt = Instant.EPOCH;

    public ServiceTokenProvider(@Value("${app.gateway.url}") String gatewayUrl,
                                @Value("${app.gateway.service-username}") String username,
                                @Value("${app.gateway.service-password}") String password) {
        this.gateway = RestClient.create(gatewayUrl);
        this.username = username;
        this.password = password;
    }

    /** "Bearer eyJ..." for the agent's own account. */
    public synchronized String authorizationHeader() {
        if (token == null || Instant.now().isAfter(renewAt)) {
            TokenResponse response = gateway.post()
                    .uri("/token")
                    .body(Map.of("username", username, "password", password, "agent", username))
                    .retrieve()
                    .body(TokenResponse.class);
            token = response.access_token();
            // renew 1 minute before it expires, so we never send an expired token
            renewAt = Instant.now().plusSeconds(Math.max(0, response.expires_in() - 60));
        }
        return "Bearer " + token;
    }
}
