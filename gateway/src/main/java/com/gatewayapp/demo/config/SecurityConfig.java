package com.gatewayapp.demo.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * TEMPORARY (week 1): lets every request through so the MCP Inspector and the agent
 * can reach /mcp without a token. Week 2 (Person B) replaces this with JWT checks
 * via the OAuth2 resource server.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())       // uses corsConfigurationSource() below
                .csrf(csrf -> csrf.disable())          // MCP clients send POSTs without a CSRF token
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
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
