package com.gatewayapp.demo.security;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Reads the "app.auth.*" lines of application.properties:
 *
 *   app.auth.users.alice=SUPPORT                                  (human: username = role)
 *   app.auth.demo-password=${DEMO_PASSWORD}                       (shared by the humans)
 *   app.auth.service-accounts.support-agent=${AGENT_SERVICE_PASSWORD}   (program: name = password)
 *   app.auth.token-lifetime=15m
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        Map<String, String> users,
        String demoPassword,
        @DefaultValue Map<String, String> serviceAccounts,
        @DefaultValue("15m") Duration tokenLifetime) {
}
