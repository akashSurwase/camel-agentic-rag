package com.example.AI.processor;

import com.example.AI.model.DailyStatusReport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

@Component
public class FailedIncidentProcessor implements Processor {

    @Override
    public void process(Exchange exchange) {

        DailyStatusReport report = exchange.getIn().getBody(DailyStatusReport.class);

        // Keep the original report for the Milvus INSERT step
        exchange.setProperty("incidentReport", report);

        String document = """
                Incident Date : %s
                
                Service : %s
                
                Operation : %s
                
                Status : %s
                
                Exception : %s
                
                Logs : %s
                """.formatted(report.getIncidentDate(), report.getServiceName(), report.getOperation(), report.getStatus(), report.getExceptionClass(), report.getLogSnippet());

        // Keep the text for later use
        exchange.setProperty("incidentText", document);

//        Map<String, Object> request = new HashMap<>();
//
//        request.put("model", "nomic-embed-text");
//        request.put("input", document);

        exchange.getMessage().setBody(document);
    }
}