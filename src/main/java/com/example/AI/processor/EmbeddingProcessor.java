package com.example.AI.processor;

import dev.langchain4j.data.embedding.Embedding;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmbeddingProcessor implements Processor {

    @Override
    public void process(Exchange exchange) {

        Object embeddingObject = exchange.getMessage().getHeader("CamelLangChain4jEmbeddingsEmbedding");

        if (embeddingObject == null) {
            throw new IllegalArgumentException("Camel LangChain4j embedding header is missing");
        }

        System.out.println("Embedding header type: " + embeddingObject.getClass().getName());

        if (!(embeddingObject instanceof Embedding)) {
            throw new IllegalArgumentException("Unexpected embedding type: " + embeddingObject.getClass().getName());
        }

        Embedding embeddingObjectTyped = (Embedding) embeddingObject;
        List<Float> embedding = embeddingObjectTyped.vectorAsList();

        if (embedding.size() != 768) {
            throw new IllegalArgumentException("Expected embedding dimension 768 but received " + embedding.size());
        }

        exchange.setProperty("embedding", embedding);
        exchange.getMessage().setBody(embedding);

    }
}