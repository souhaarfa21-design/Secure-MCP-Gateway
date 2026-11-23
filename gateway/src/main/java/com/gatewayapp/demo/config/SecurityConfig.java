package com.gatewayapp.demo.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Who may call what on the gateway.
 *
 *  - POST /token          open to everyone: it's where you log in to GET a token
 *  - /actuator/health     open: "is the gateway up?"
 *  - everything else      (including /mcp, the tools) needs a valid JWT:
 *                         Authorization: Bearer eyJ...
 *
 * The gateway is an OAuth2 "resource server": it doesn't log people in with a session,
 * it only checks the token that comes with each request (signature, expiry, issuer).
 * Missing, expired or tampered token → 401 Unauthorized.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())       // uses corsConfigurationSource() below
                .csrf(csrf -> csrf.disable())          // no cookies/sessions → CSRF can't happen
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/token", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(roleFromToken())));
        return http.build();
    }

    /**
     * Our tokens carry the role in a "role" claim (e.g. "SUPPORT").
     * Spring Security turns it into the authority "ROLE_SUPPORT", so later we can write
     * rules like hasRole("ADMIN") (Wednesday's role policy).
     */
    private static JwtAuthenticationConverter roleFromToken() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

    /**
     * CORS: which web pages (origins) may call the gateway from a browser.
     * 6274 = MCP Inspector, 5173 = our React app (Vite dev server).
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(
                "http://localhost:6274", "http://127.0.0.1:6274",
                "http://localhost:5173", "http://127.0.0.1:5173"));
        cors.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        // the browser must be allowed to read the MCP session id the server sends back
        cors.setExposedHeaders(List.of("Mcp-Session-Id"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
