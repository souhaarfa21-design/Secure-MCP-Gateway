package com.example.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * POST /api/chat  {"message": "How many customers are on the Pro plan?"}
 *                 Authorization: Bearer <the user's token from the gateway's /token>
 * →               {"answer": "There are 43 customers on the Pro plan."}
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    public record ChatRequest(String message) {}
    public record ChatResponse(String answer) {}

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request,
                             @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        // No user token → no question. Otherwise the tool calls would fall back to the agent's
        // own SERVICE token, and the gateway couldn't tell which human is asking.
        // (The agent doesn't check the token itself: the gateway does, on every tool call.)
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Log in first: missing Bearer token");
        }
        // .call() runs the whole agent loop: the AI may call gateway tools
        // several times before it writes the final answer. GatewayAuthConfig adds
        // this request's token to each of those calls.
        String answer = chatClient.prompt()
                .user(request.message())
                .call()
                .content();
        return new ChatResponse(answer);
    }
}
