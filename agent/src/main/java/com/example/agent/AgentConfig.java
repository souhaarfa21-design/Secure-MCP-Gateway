package com.example.agent;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    /**
     * The support bot's "brain".
     *
     * mcpTools is created automatically by Spring AI's MCP client: it connected to the
     * gateway at startup, asked "which tools do you have?" and wrapped each one so the
     * ChatClient can call it. defaultToolCallbacks(...) hands those tools to the AI.
     */
    @Bean
    ChatClient chatClient(ChatClient.Builder builder, SyncMcpToolCallbackProvider mcpTools) {
        return builder
                .defaultSystem("""
                        You are a customer-support assistant for a software company.
                        You can read the support database (tables: customers, tickets) only through your tools.
                        Before writing SQL, call get_schema if you are unsure of table or column names.
                        Plan values are stored in upper case: FREE, BASIC, PRO, ENTERPRISE.
                        When asked to handle a ticket: read it with query_database, do what the
                        customer needs, then answer them with reply_ticket.
                        Answer briefly and only with facts returned by the tools.
                        """)
                .defaultToolCallbacks(mcpTools)
                // gpt-oss is a "reasoning" model: Groq sends back its private reasoning text.
                // Spring AI would then send that text back to Groq with the tool result, and
                // Groq rejects it ("reasoning_content is unsupported"). So we ask Groq not to
                // include the reasoning at all.
                .defaultOptions(OpenAiChatOptions.builder()
                        .extraBody(Map.of("include_reasoning", false)))
                .build();
    }
}
