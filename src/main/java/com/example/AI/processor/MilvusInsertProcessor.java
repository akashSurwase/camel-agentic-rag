package com.example.AI.processor;

import com.example.AI.model.DailyStatusReport;
import io.milvus.param.dml.InsertParam;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MilvusInsertProcessor implements Processor {

    @Override
    public void process(Exchange exchange) {

        DailyStatusReport report = exchange.getProperty("incidentReport", DailyStatusReport.class);

        List<Float> embedding = exchange.getProperty("embedding", List.class);

        if (report == null) {
            throw new IllegalArgumentException("incidentReport is missing from Exchange");
        }

        if (embedding == null) {
            throw new IllegalArgumentException("embedding is missing from Exchange");
        }

        if (embedding.size() != 768) {
            throw new IllegalArgumentException("Expected 768 dimensional embedding but received " + embedding.size());
        }

        /*
         * Column order MUST match the Milvus collection schema:
         *
         * id
         * incidentDate
         * serviceName
         * operation
         * status
         * exception
         * logs
         * embedding
         *
         * ID is AutoID, so we don't insert it.
         */

        List<InsertParam.Field> fields = new ArrayList<>();

        fields.add(new InsertParam.Field("incidentDate", List.of(report.getIncidentDate())));

        fields.add(new InsertParam.Field("serviceName", List.of(report.getServiceName())));

        fields.add(new InsertParam.Field("operation", List.of(report.getOperation())));

        fields.add(new InsertParam.Field("status", List.of(report.getStatus())));

        fields.add(new InsertParam.Field("exception", List.of(report.getExceptionClass())));

        fields.add(new InsertParam.Field("logs", List.of(report.getLogSnippet())));

        fields.add(new InsertParam.Field("embedding", List.of(embedding)));

        InsertParam insertParam = InsertParam.newBuilder().withCollectionName("historical_incidents").withFields(fields).build();

        exchange.getMessage().setBody(insertParam);
    }
}