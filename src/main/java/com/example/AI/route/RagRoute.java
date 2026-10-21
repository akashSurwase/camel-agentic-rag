package com.example.AI.route;

import com.example.AI.processor.DuplicateRemovalProcessor;
import com.example.AI.processor.RagResponseProcessor;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class RagRoute extends RouteBuilder {

    private final RagResponseProcessor ragResponseProcessor;
    private final DuplicateRemovalProcessor duplicateRemovalProcessor;

    public RagRoute(RagResponseProcessor ragResponseProcessor, DuplicateRemovalProcessor duplicateRemovalProcessor) {
        this.ragResponseProcessor = ragResponseProcessor;
        this.duplicateRemovalProcessor = duplicateRemovalProcessor;
    }

    @Override
    public void configure() {

        from("direct:generate-rag-answer")
                .routeId("generate-rag-answer")

                /*
                 * =================================================
                 * BUILD RAG CONTEXT
                 * =================================================
                 */

                .log("Building RAG context from Milvus results")

                .process(exchange -> {
                    String question = exchange.getProperty("userQuestion", String.class);
                    if (question == null || question.isBlank()) {
                        throw new IllegalArgumentException("userQuestion is missing");
                    }
                })

                /*
                 * Convert Milvus SearchResult object to JSON.
                 *
                 * DuplicateRemovalProcessor currently expects JSON.
                 */

                .marshal().json()
                .process(duplicateRemovalProcessor)
                .log("Unique relevant incidents: ${exchangeProperty.uniqueIncidentCount}")
                .log("Weak incidents filtered: ${exchangeProperty.filteredIncidentCount}")
                .log("RAG context: ${exchangeProperty.ragContext}")

                /*
                 * =================================================
                 * BUILD RAG PROMPT
                 * =================================================
                 */

                .process(exchange -> {
                    String question = exchange.getProperty("userQuestion", String.class);
                    String context = exchange.getProperty("ragContext", String.class);

                    /*
                     * Handle no relevant incidents.
                     */

                    if (context == null || context.isBlank()) {

                        context =
                                "No relevant historical incidents "
                                        + "were found.";
                    }

                    String prompt =
                            """
                            You are an incident analysis assistant for a production
                            support team.
                            
                            Use the historical incident information as the primary
                            source for understanding what happened.
                            
                            Historical incidents:
                            
                            %s
                            
                            User question:
                            
                            %s
                            
                            Instructions:
                            
                            1. Historical Evidence
                            
                            - Identify the most relevant historical incident(s).
                            - Clearly explain what happened using only information
                              supported by the historical incident data.
                            - Mention the service, operation, exception, date, status,
                              and relevant logs when available.
                            - Do not claim an unverified root cause as fact.
                            
                            2. Resolution Guidance
                            
                            - If the user asks how to resolve, troubleshoot, fix,
                              or prevent the issue, provide practical and actionable
                              troubleshooting recommendations based on the observed
                              exception, error, service, operation, and logs.
                            - Adapt the troubleshooting recommendations to the type
                              of problem identified in the incident.
                            - Do not assume that every incident is a database or JDBC
                              problem.
                            - Do not claim that a recommendation was the actual root
                              cause unless the historical data confirms it.
                            - Clearly distinguish confirmed historical facts from
                              possible causes and recommended actions.
                            
                            3. Problem-Specific Troubleshooting
                            
                            Determine the technical area from the incident information
                            and provide troubleshooting appropriate to that problem.
                            
                            Examples:
                            
                            - For JDBC/database errors:
                              check database availability, network connectivity,
                              JDBC URL, credentials, connection pool, database
                              connection limits, JDBC driver, and database logs.
                            
                            - For HTTP/API errors:
                              check HTTP status codes, endpoint availability,
                              request/response payloads, authentication, downstream
                              services, timeout configuration, and application logs.
                            
                            - For Apache Camel route errors:
                              check route status, failing processor/component,
                              endpoint configuration, exchange data, exception stack
                              trace, downstream endpoints, timeouts, and Camel logs.
                            
                            - For Filebeat/ELK errors:
                              check Filebeat configuration, connectivity to Logstash
                              or Elasticsearch, certificates, authentication,
                              index/template/ILM configuration, disk space, and
                              Filebeat logs.
                            
                            - For Elasticsearch errors:
                              check cluster health, node availability, disk usage,
                              shard allocation, index status, JVM/memory pressure,
                              cluster logs, and relevant Elasticsearch settings.
                            
                            - For application exceptions:
                              analyze the exception type and stack trace and provide
                              troubleshooting steps relevant to that exception.
                            
                            - For network/connectivity errors:
                              check DNS resolution, ports, firewall rules, routing,
                              TLS/certificates, connectivity from the application host,
                              and service availability.
                            
                            - For any other error:
                              infer the appropriate technical area from the available
                              incident evidence and provide relevant troubleshooting
                              steps.
                            
                            Do not blindly apply any troubleshooting category if it
                            does not relate to the incident.
                            
                            4. Root Cause
                            
                            - Clearly distinguish:
                              a) Confirmed facts
                              b) Possible causes
                              c) Recommended troubleshooting
                            
                            - If the historical data does not contain enough information
                              to determine the actual root cause, explicitly state that.
                            - Never invent a root cause.
                            
                            5. What to Check Next
                            
                            Recommend the most relevant logs, systems, configuration,
                            metrics, commands, or teams that should be checked next
                            based on the specific incident.
                            
                            6. Response Format
                            
                            Historical Finding:
                            <what actually happened based on the historical data>
                            
                            Likely Technical Area:
                            <technical area relevant to this incident>
                            
                            Possible Causes:
                            <possible causes only when they are reasonable based on
                            the observed error; clearly mark them as unconfirmed>
                            
                            Recommended Troubleshooting:
                            1. ...
                            2. ...
                            3. ...
                            
                            What to Check Next:
                            <specific logs, systems, configuration, metrics,
                            commands, or teams to check>
                            
                            Confidence:
                            <High / Medium / Low>
                            <brief explanation>
                            
                            Keep the response concise, technically accurate, and useful
                            for a production operations support engineer.
                            
                            Answer:
                            """.formatted(context, question);

                    exchange.setProperty("ragPrompt", prompt);
                    exchange.getMessage().setBody(prompt);
                })
                .log("Sending RAG prompt to Qwen3:4b through Camel OpenAI component")


//        If you use streaming=true
//        The answer arrives in chunks:
//        Temperature is use for creative ans

                .to("openai:chat-completion?temperature=0.2&streaming=false&storeFullResponse=true")
                .log("Qwen response: ${body}")
                .process(ragResponseProcessor)
                .marshal().json()
                .log("Final RAG response: ${body}");
    }
}