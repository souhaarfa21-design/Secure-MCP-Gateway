package com.gatewayapp.demo.audit;

import java.util.Collection;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * The audit log: one row in audit_log for EVERY tool call, without touching the tool code.
 *
 * "@Around" = this method runs AROUND each tool method: it starts a timer, lets the tool run
 * (pjp.proceed()), then writes who called which tool, what happened and how long it took.
 * Which methods? Every method annotated with @McpTool, so a new tool is audited automatically.
 *
 * decision column:  ALLOWED = the tool ran  ·  ERROR = the tool failed
 *                   (BLOCKED comes later, when the guards refuse a call)
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final JdbcTemplate jdbc;

    public AuditAspect(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Around("@annotation(tool)")
    public Object audit(ProceedingJoinPoint pjp, McpTool tool) throws Throwable {
        long start = System.nanoTime();
        Caller caller = currentCaller();
        try {
            Object result = pjp.proceed();                         // ← the tool really runs here
            Integer rows = (result instanceof Collection<?> c) ? c.size() : null;
            save(caller, tool.name(), "ALLOWED", null, rows, start);
            return result;
        } catch (Throwable e) {
            save(caller, tool.name(), "ERROR", e.getMessage(), null, start);
            throw e;                                               // the caller still sees the error
        }
    }

    /** Who is calling: read from the JWT that Spring Security already checked. */
    private static Caller currentCaller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return new Caller(jwt.getName(), jwt.getToken().getClaimAsString("agent"));
        }
        return new Caller("anonymous", null);
    }

    private void save(Caller caller, String tool, String decision, String reason, Integer rows, long start) {
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        try {
            jdbc.update("""
                    INSERT INTO audit_log (username, agent, tool, decision, reason, row_count, duration_ms)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, caller.username(), caller.agent(), tool, decision, reason, rows, durationMs);
        } catch (RuntimeException e) {
            // a broken audit write must not break the tool call itself; it shows up in the logs
            log.error("Could not write audit row for tool {}", tool, e);
        }
    }

    private record Caller(String username, String agent) {}
}
