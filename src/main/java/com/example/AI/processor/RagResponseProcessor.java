package com.example.AI.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RagResponseProcessor implements Processor {

    @Override
    public void process(Exchange exchange) {

        String question = exchange.getProperty("userQuestion", String.class);
        String answer = exchange.getMessage().getBody(String.class);

        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("No answer returned from Qwen");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("question", question);
        response.put("answer", answer);
        response.put("uniqueIncidentCount", exchange.getProperty("uniqueIncidentCount"));
        response.put("filteredIncidentCount", exchange.getProperty("filteredIncidentCount"));
        exchange.getMessage().setBody(response);
    }
}