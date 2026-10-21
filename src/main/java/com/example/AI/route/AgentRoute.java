package com.example.AI.route;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AgentRoute extends RouteBuilder {

    @Override
    public void configure() {

        restConfiguration().component("servlet").bindingMode(RestBindingMode.json).enableCORS(true).corsHeaderProperty("Access-Control-Allow-Origin", "*").corsHeaderProperty("Access-Control-Allow-Headers", "Content-Type").corsHeaderProperty("Access-Control-Allow-Methods", "POST,OPTIONS");

        rest().post("/incidents/agent").consumes("application/json").produces("application/json").to("direct:incident-agent");

        from("direct:incident-agent").routeId("incident-agent-api")

                .process(exchange -> {

                    Map<String, Object> request = exchange.getMessage().getBody(Map.class);

                    String question = (String) request.get("question");

                    if (question == null || question.isBlank()) {
                        throw new IllegalArgumentException("question is required");
                    }

                    exchange.getMessage().setBody(question);
                })

                /*
                 * System prompt for Qwen Agent
                 */
                .setHeader("CamelLangChain4jAgentSystemMessage", constant("""
                        You are an incident analysis agent.
                        
                        Use search_historical_incidents when:
                        - The question involves historical incidents.
                        - The question asks what happened previously.
                        - The answer requires internal incident data.
                        
                        Use tavily_search when:
                        - The question requires current public information.
                        - The question asks about latest releases,
                          current documentation,
                          current recommendations,
                          or recent external information.
                        
                        Use both tools when:
                        - The question requires historical internal
                          evidence AND current external information.
                        
                        Do not use a tool when:
                        - You can answer confidently without external
                          or historical data.
                        
                        When using both tools:
                        - Clearly distinguish internal historical
                          evidence from external web information.
                        - Do not treat web information as internal
                          evidence.
                        - Do not treat historical incidents as proof
                          of current conditions.
                        
                        Response format:
                        - Answer the user's question directly and concisely.
                        - When historical and external information are both used,
                          clearly separate the historical evidence from the
                          current external information.
                        - Do not include a "Next Steps" section.
                        - Do not include a "Note" section.
                        - Do not include generic closing remarks.
                        - Do not repeat information unnecessarily.
                        - Only provide recommendations that are relevant
                          to the user's question.
                        
                        Never invent facts.
                        Only state facts supported by the user question
                        or tool results.
                        """))

                .log("Sending question to Qwen Agent: ${body}")
                .process(exchange -> {
                    exchange.setProperty("agentStartTime", System.nanoTime());})
                .to("langchain4j-agent:incident-agent?agentConfiguration=#incidentAgentConfiguration&tags=incident,web")
                .process(exchange -> {
                    Long startTime = exchange.getProperty("agentStartTime", Long.class);

                    long elapsedNanos = System.nanoTime() - startTime;
                    long elapsedMillis = elapsedNanos / 1_000_000;

                    exchange.setProperty("agentResponseTimeMs", elapsedMillis);

                    exchange.getMessage().setHeader("Agent-Response-Time-Ms", elapsedMillis);

                    System.out.println("========================================");
                    System.out.println("Agent response time: " + elapsedMillis + " ms");
                    System.out.println("Agent response time: " + (elapsedMillis / 1000.0) + " seconds");
                    System.out.println("========================================");
                })
                .log("Agent final response: ${body}")
                .log("Agent response time: ${exchangeProperty.agentResponseTimeMs} ms");
    }
}