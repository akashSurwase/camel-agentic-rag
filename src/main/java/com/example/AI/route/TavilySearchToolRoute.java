package com.example.AI.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TavilySearchToolRoute extends RouteBuilder {

    @Value("${tavily.api.key}")
    private String tavilyApiKey;

    @Value("${tavily.api.url}")
    private String tavilyApiUrl;

    @Override
    public void configure() {

        from("ai-tool:tavily_search"
                + "?tags=web"
                + "&description=Search the current public web for up-to-date information. Use this tool for current events, latest releases, recent documentation, news, or information that cannot be found in historical LEN incidents."
                + "&parameter.query.type=string"
                + "&parameter.query.description=The web search query"
                + "&parameter.query.required=true")

                .routeId("ai-tool-tavily-search")

                .log("========================================")
                .log("Tavily AI Tool called")
                .log("Tavily query: ${header.query}")
                .log("Tavily Question: ${header.question}")
                .log("========================================")

                // Build Tavily request
                .process(exchange -> {

                    String query = exchange.getMessage().getHeader("query", String.class);

                    if (query == null || query.isBlank()) {
                        throw new IllegalArgumentException("query is required");
                    }

                    String requestBody = """
                            {
                              "query": "%s",
                              "search_depth": "basic",
                              "max_results": 5,
                              "include_answer": false
                            }
                            """.formatted(query.replace("\"", "\\\""));

                    exchange.getMessage().setBody(requestBody);
                })

                // Tavily authentication
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setHeader("Authorization", constant("Bearer " + tavilyApiKey))

                // ========================================
                // START TAVILY TIMER
                // ========================================
                .process(exchange -> {
                    exchange.setProperty("tavilyStartTime", System.nanoTime());
                })

                // Call Tavily API
                .toD(tavilyApiUrl + "?bridgeEndpoint=true" + "&throwExceptionOnFailure=true")

                // ========================================
                // END TAVILY TIMER
                // ========================================
                .process(exchange -> {
                    long start = exchange.getProperty("tavilyStartTime", Long.class);
                    double elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0;
                    exchange.setProperty("tavilyResponseTimeSeconds", elapsedSeconds);
                    System.out.println("========== TAVILY TIMING ==========");
                    System.out.printf("Tavily response time: %.2f seconds%n", elapsedSeconds);
                    System.out.println("===================================");
                })

                .log("Tavily raw response: ${body}")

                // Convert response to cleaner text
                .process(exchange -> {

                    String json = exchange.getMessage().getBody(String.class);

                    ObjectMapper mapper = new ObjectMapper();

                    JsonNode root = mapper.readTree(json);

                    StringBuilder result = new StringBuilder();

                    result.append("Web search results:\n\n");

                    JsonNode results = root.get("results");

                    if (results != null && results.isArray()) {

                        int index = 1;

                        for (JsonNode item : results) {

                            result.append(index++).append(". ").append(item.path("title").asText()).append("\n");

                            result.append("URL: ").append(item.path("url").asText()).append("\n");

                            result.append("Content: ").append(item.path("content").asText()).append("\n\n");
                        }
                    }

                    exchange.getMessage().setBody(result.toString());
                })

                .log("Tavily formatted result returned to Agent");
    }
}