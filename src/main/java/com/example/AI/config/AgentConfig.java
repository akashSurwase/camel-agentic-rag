package com.example.AI.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean(name = "incidentChatModel")
    public ChatModel incidentChatModel() {

        return OpenAiChatModel.builder()
                .baseUrl("http://localhost:11434/v1")
                .apiKey("ollama")
                .modelName("qwen3:8b")
                .temperature(0.2)
                .maxTokens(700)
                .build();
    }

    @Bean(name = "incidentEmbeddingModel")
    public EmbeddingModel incidentEmbeddingModel() {

        return OpenAiEmbeddingModel.builder()
                .baseUrl("http://localhost:11434/v1")
                .apiKey("ollama")
                .modelName("nomic-embed-text")
                .build();
    }

    @Bean(name = "incidentAgentConfiguration")
    public AgentConfiguration incidentAgentConfiguration(ChatModel incidentChatModel) {

        return new AgentConfiguration()
                .withChatModel(incidentChatModel)
                .withMaxToolCallingRoundTrips(4)
                .withExecuteToolsConcurrently();
    }

    @Bean
    public CommandLineRunner verifyEmbeddingModel(EmbeddingModel embeddingModel) {

        return args -> {

            System.out.println("==========================================");

            System.out.println("EmbeddingModel = " + embeddingModel);

            System.out.println("Embedding dimension = " + embeddingModel.dimension());

            System.out.println("==========================================");
        };
    }

}


