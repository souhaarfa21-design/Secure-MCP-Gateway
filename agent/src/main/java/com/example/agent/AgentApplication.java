package com.example.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class AgentApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext run = SpringApplication.run(AgentApplication.class, args);
	}
};
