package com.gatewayapp.demo.tools;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The tools the gateway offers to AI agents over MCP.
 * Every public method marked @McpTool becomes a tool that an MCP client
 * (the MCP Inspector, or our agent app on Thursday) can discover and call.
 *
 * WARNING: there is no security yet. query_database runs whatever SQL it gets.
 * That is on purpose for week 1 (it's the "before" demo); weeks 2–3 add the checks.
 */
@Component
public class DatabaseTools {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTools.class);

    private final JdbcTemplate jdbc;

    public DatabaseTools(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @McpTool(name = "get_schema",
            description = "Lists the tables of the support database and their columns. "
                    + "Call this first to know which tables and columns exist before writing SQL.")
    public List<Map<String, Object>> getSchema() {
        log.info("Tool called: get_schema");
        return jdbc.queryForList("""
                SELECT table_name, column_name, data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name IN ('customers', 'tickets', 'ticket_replies')
                ORDER BY table_name, ordinal_position
                """);
    }

    @McpTool(name = "query_database",
            description = "Runs a read-only SQL SELECT query on the PostgreSQL support database "
                    + "and returns the rows. Tables: customers, tickets.")
    public List<Map<String, Object>> queryDatabase(
            @McpToolParam(description = "A single PostgreSQL SELECT statement", required = true) String sql) {
        // JdbcTemplate (not JPA) on purpose: the guard will later need the exact SQL text.
        log.info("Tool called: query_database  sql={}", sql);
        return jdbc.queryForList(sql);
    }

    @McpTool(name = "reply_ticket",
            description = "Sends a reply message to the customer who opened a support ticket.")
    public String replyTicket(
            @McpToolParam(description = "The id of the ticket to answer", required = true) long ticketId,
            @McpToolParam(description = "The reply text the customer will receive", required = true) String message) {
        // WARNING (week 1): sent immediately, no check. This is the way data can leave the company.
        // Week 2 puts it behind a human approval queue.
        log.info("Tool called: reply_ticket  ticketId={}  message={}", ticketId, message);
        jdbc.update("INSERT INTO ticket_replies (ticket_id, body) VALUES (?, ?)", ticketId, message);
        return "Reply sent to the customer of ticket " + ticketId;
    }
}
