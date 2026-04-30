package com.example.aiinterview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiInterviewAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiInterviewAssistantApplication.class, args);
    }
}
