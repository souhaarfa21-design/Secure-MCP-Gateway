package com.example.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/chat  {"message": "How many customers are on the Pro plan?"}
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
    public ChatResponse chat(@RequestBody ChatRequest request) {
        // .call() runs the whole agent loop: the AI may call gateway tools
        // several times before it writes the final answer.
        String answer = chatClient.prompt()
                .user(request.message())
                .call()
                .content();
        return new ChatResponse(answer);
    }
}
