package com.example.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgentApplication.class, args);
	}

	@Bean
	CommandLineRunner test(ChatClient.Builder builder) {
		return args -> {
			String answer = builder.build().prompt()
					.user("Say hello in one sentence.")
					.call()
					.content();
			System.out.println(answer);
		};
	}
}