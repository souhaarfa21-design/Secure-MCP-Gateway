package com.example.agent;

import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Forwards the user's token from the agent app to the gateway, on every MCP request.
 *
 *   Browser ──"Authorization: Bearer <alice's token>"──► Agent (/api/chat)
 *   Agent   ──"Authorization: Bearer <alice's token>"──► Gateway (/mcp, each tool call)
 *
 * Spring AI 2.0 has no "add this header to the connection" setting, and a fixed header would
 * be wrong anyway: the token is different for every user and every request. So it takes 2 steps:
 *
 *  1. PICK UP (McpClientCustomizer): just before each MCP call, on the thread that is handling
 *     alice's /api/chat request, read her "Authorization" header and put it in the call's
 *     "transport context" — a small bag of values that travels with that one MCP call.
 *
 *  2. ATTACH (McpSyncHttpClientRequestCustomizer): when the MCP library builds the HTTP request
 *     to the gateway, take the token out of the bag and set the Authorization header.
 *     The bag is needed because the library may send the request from ANOTHER thread, where
 *     alice's /api/chat request is no longer visible.
 *
 * No user token in the bag (the startup connection, before anyone has logged in)?
 * Then the agent's own SERVICE token is used instead (see ServiceTokenProvider).
 */
@Configuration
public class GatewayAuthConfig {

    /** Name of our value inside the transport context "bag". */
    static final String USER_AUTHORIZATION = "user-authorization";

    /** Step 1: put the current user's "Bearer ..." into the context of each MCP call. */
    @Bean
    McpClientCustomizer<McpClient.SyncSpec> pickUpUserToken() {
        return (connectionName, spec) -> spec.transportContextProvider(() -> {
            String authorization = authorizationOfCurrentHttpRequest();
            return authorization == null
                    ? McpTransportContext.EMPTY
                    : McpTransportContext.create(Map.of(USER_AUTHORIZATION, authorization));
        });
    }

    /** Step 2: write the token into every HTTP request the MCP client sends to the gateway. */
    @Bean
    McpSyncHttpClientRequestCustomizer attachToken(ServiceTokenProvider serviceToken) {
        return (request, method, uri, body, context) -> {
            Object user = (context == null) ? null : context.get(USER_AUTHORIZATION);
            String authorization = (user != null) ? user.toString() : serviceToken.authorizationHeader();
            request.setHeader(HttpHeaders.AUTHORIZATION, authorization);
        };
    }

    /** The "Authorization" header of the /api/chat request being handled on this thread, if any. */
    private static String authorizationOfCurrentHttpRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                return header;
            }
        }
        return null;
    }
}
