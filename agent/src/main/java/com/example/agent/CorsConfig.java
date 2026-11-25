package com.example.agent;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS: lets our React page (Vite dev server on port 5173) call /api/chat from the browser.
 *
 * Without this, the browser blocks the answer: the page runs on localhost:5173 and the agent
 * on localhost:8081, which counts as a different "origin". The agent must say "5173 is allowed".
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization");   // Authorization = the user's token
    }
}
