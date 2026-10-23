package com.example.AI.route;

import com.example.AI.processor.EmbeddingProcessor;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class IncidentSearchToolRoute extends RouteBuilder {

    private final EmbeddingProcessor embeddingProcessor;

    public IncidentSearchToolRoute(EmbeddingProcessor embeddingProcessor) {
        this.embeddingProcessor = embeddingProcessor;
    }

    @Override
    public void configure() {

        from("ai-tool:search_historical_incidents?description=Search historical production incidents using semantic similarity. Use this tool when you need historical incident information, previous failures, exceptions, logs, services, or operations." +
                "&tags=incident" +
                "&parameter.question=string" +
                "&parameter.question.description=The incident or technical question to search for in historical incidents&parameter.question.required=true")
                .routeId("ai-tool-search-historical-incidents")

                .log("========================================")
                .log("Historical incident search AI Tool called")
                .log("Historical incident search query: ${header.query}")
                .log("Historical incident search question    : ${header.question}")
                .log("========================================")


                .process(exchange -> {

                    String question = exchange.getMessage().getHeader("question", String.class);

                    if (question == null || question.isBlank()) {
                        throw new IllegalArgumentException("question is required");
                    }

                    exchange.setProperty("userQuestion", question);
                    exchange.getMessage().setBody(question);
                })

                /*
                 * Your existing embedding logic
                 */.to("langchain4j-embeddings:query-embedding")

                .process(exchange -> {

                    Object embeddingObject = exchange.getMessage().getHeader("CamelLangChain4jEmbeddingsEmbedding");

                    if (embeddingObject == null) {
                        throw new IllegalArgumentException("Embedding was not generated");
                    }
                })

                /*
                 * Convert LangChain4j embedding
                 * into your existing Exchange property
                 */.process(embeddingProcessor)

                /*
                 * Existing Milvus search
                 */.to("direct:search-similar-incidents")

                .log("Historical incidents returned: ${body}");
    }

}