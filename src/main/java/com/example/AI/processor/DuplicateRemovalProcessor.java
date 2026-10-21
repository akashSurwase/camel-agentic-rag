package com.example.AI.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class DuplicateRemovalProcessor implements Processor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${rag.similarity.threshold:0.65}")
    private double similarityThreshold;


    @Override
    public void process(Exchange exchange) throws Exception {
        int filteredIncidentCount = 0;

        /*
         * Milvus search result is currently the
         * message body.
         */
        String milvusResult = exchange.getMessage().getBody(String.class);
        JsonNode root = objectMapper.readTree(milvusResult);
        StringBuilder context = new StringBuilder();
        /*
         * Set stores unique incident keys.
         */
        Set<String> uniqueIncidents = new HashSet<>();
        JsonNode records = root.get("rowRecords");

        if (records != null && records.isArray() && records.size() > 0) {
            JsonNode incidents = records.get(0);
            for (JsonNode incident : incidents) {
                JsonNode fields = incident.get("fieldValues");
                if (fields == null) {
                    continue;
                }

                /*
                 * Extract incident fields.
                 */

                String incidentDate = fields.path("incidentDate").asText();
                String serviceName = fields.path("serviceName").asText();
                String operation = fields.path("operation").asText();
                String status = fields.path("status").asText();
                String exception = fields.path("exception").asText();
                String logs = fields.path("logs").asText();
                double score = fields.path("score").asDouble();

                /*
                 * Ignore weak semantic matches.
                 */

                if (score < similarityThreshold) {
                    filteredIncidentCount++;
                    continue;
                }

                /*
                 * Create a unique key.
                 *
                 * We do not include id because
                 * duplicate records can have
                 * different Milvus IDs.
                 */

                String uniqueKey = serviceName + "|" + operation + "|" + exception + "|" + logs;

                /*
                 * If add() returns false,
                 * this incident already exists.
                 */

                if (!uniqueIncidents.add(uniqueKey)) {
                    continue;
                }

                /*
                 * Add only unique incidents
                 * to the RAG context.
                 */

                context.append("Incident Date: ").append(incidentDate).append("\n");
                context.append("Service: ").append(serviceName).append("\n");
                context.append("Operation: ").append(operation).append("\n");
                context.append("Status: ").append(status).append("\n");
                context.append("Exception: ").append(exception).append("\n");
                context.append("Logs: ").append(logs).append("\n");
                context.append("Similarity Score: ").append(score).append("\n");
                context.append("-------------------------\n");
            }
        }

        exchange.setProperty("ragContext", context.toString());
        exchange.setProperty("filteredIncidentCount", filteredIncidentCount);
        exchange.setProperty("uniqueIncidentCount", uniqueIncidents.size());
    }
}