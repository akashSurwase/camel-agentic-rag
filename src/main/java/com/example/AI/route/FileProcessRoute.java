package com.example.AI.route;

import com.example.AI.model.DailyStatusReport;
import com.example.AI.processor.EmbeddingProcessor;
import com.example.AI.processor.FailedIncidentProcessor;
import com.example.AI.processor.MilvusInsertProcessor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milvus.Milvus;
import org.apache.camel.component.milvus.MilvusAction;
import org.apache.camel.model.dataformat.BindyType;
import org.springframework.stereotype.Component;

@Component
public class FileProcessRoute extends RouteBuilder {

    private final FailedIncidentProcessor failedIncidentProcessor;
    private final EmbeddingProcessor embeddingProcessor;
    private final MilvusInsertProcessor milvusInsertProcessor;

    public FileProcessRoute(FailedIncidentProcessor failedIncidentProcessor, EmbeddingProcessor embeddingProcessor, MilvusInsertProcessor milvusInsertProcessor) {

        this.failedIncidentProcessor = failedIncidentProcessor;
        this.embeddingProcessor = embeddingProcessor;
        this.milvusInsertProcessor = milvusInsertProcessor;
    }

    @Override
    public void configure() {

        onException(Exception.class).
                handled(true).
                log("Error while processing file: ${exception.message}");


        /*
         * =========================================================
         * DAILY STATUS REPORT
         * =========================================================
         */

        from("{{daily.report.filepath}}?delete=false&fileName=daily_status_report_with_date.csv").routeId("daily-status-route")

                .unmarshal().bindy(BindyType.Csv, DailyStatusReport.class)
                .split(body())

                .choice()
                    .when(simple("${body.status} == 'FAILED'"))

                /*
                 * -------------------------------------------------
                 * STEP 1: Build incident document
                 * -------------------------------------------------
                 */

                .process(failedIncidentProcessor)

                .log("langchain4j-embeddings Request Body:: ${body}")
                .to("langchain4j-embeddings:query-embedding")
                .log("langchain4j-embeddings Response Body:: ${body}")
                .log("Headers after Camel LangChain4j: ${headers}")


                /*
                 * -------------------------------------------------
                 * STEP 3: Extract/store embedding
                 * -------------------------------------------------
                 */

                .process(embeddingProcessor)
                .log("Embedding header = ${header.CamelLangChain4jEmbeddingsEmbedding}")
                .log("Embedding body: ${body}")

                /*
                 * -------------------------------------------------
                 * STEP 4: Insert incident into Milvus
                 * -------------------------------------------------
                 */
                .to("direct:insert-historical-incident")
                .otherwise()
                .log("Skipping non-FAILED incident: ${body}")
                .end();


        /*
         * =========================================================
         * MILVUS INSERT
         * =========================================================
         */

        from("direct:insert-historical-incident")
                .routeId("insert-historical-incident")

                .process(milvusInsertProcessor)
                .setHeader(Milvus.Headers.ACTION)
                .constant(MilvusAction.INSERT)
                .to("milvus:historical_incidents")
                .log("Incident inserted into Milvus successfully");
    }
}