package com.example.AI.route;

import com.example.AI.processor.GlobalExceptionProcessor;
import io.milvus.param.highlevel.dml.SearchSimpleParam;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milvus.Milvus;
import org.apache.camel.component.milvus.MilvusAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class MilvusRoute extends RouteBuilder {

    private static final String COLLECTION_NAME = "historical_incidents";
    private static final int EMBEDDING_DIMENSION = 768;


    @Autowired
    private GlobalExceptionProcessor globalExceptionProcessor;

    public MilvusRoute(GlobalExceptionProcessor globalExceptionProcessor) {
        this.globalExceptionProcessor = globalExceptionProcessor;
    }

    @Override
    public void configure() {

        /*
         * =========================================================
         * MILVUS SEMANTIC SEARCH
         * =========================================================
         *
         *
         */
        onException(Exception.class).log("error occurred while processing exception: ${exception.message}").handled(true).process(globalExceptionProcessor);


        from("direct:search-similar-incidents").routeId("search-similar-incidents").log("Starting Milvus similarity search")

                /*
                 * Tell Camel Milvus that we want SEARCH.
                 */

                .setHeader(Milvus.Headers.ACTION).constant(MilvusAction.SEARCH)
                /*
                 * Camel LangChain4j has already generated
                 * the embedding.
                 *
                 * Read the vector from the Camel header.
                 */

                .process(exchange -> {
                    float[] vector = exchange.getMessage().getHeader("CamelLangChain4jEmbeddingsVector", float[].class);
                    if (vector == null) {
                        throw new IllegalArgumentException("Embedding vector is missing from Camel message");
                    }


                    /*
                     * Validate dimension.
                     */

                    if (vector.length != EMBEDDING_DIMENSION) {
                        throw new IllegalArgumentException("Expected embedding dimension " + EMBEDDING_DIMENSION + " but received " + vector.length);
                    }


                    /*
                     * Convert float[] to List<Float>
                     * because SearchSimpleParam expects vectors
                     * in List<List<Float>> form.
                     */

                    List<Float> queryVector = new java.util.ArrayList<>();
                    for (float value : vector) {
                        queryVector.add(value);
                    }

                    /*
                     * Create Milvus SEARCH parameters.
                     */
                    SearchSimpleParam searchParam = createSearchParam(queryVector);
                    exchange.getMessage().setBody(searchParam);
                })


                /*
                 * Send search request to Milvus.
                 */

                .process(exchange -> {
                    exchange.setProperty("milvusStartTime", System.nanoTime());
                })
                .to("milvus:" + COLLECTION_NAME).process(exchange -> {

                    long start = exchange.getProperty("milvusStartTime", Long.class);

                    double elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0;

                    exchange.getMessage() .setHeader( "MilvusResponseTimeSeconds", elapsedSeconds );

                    System.out.println("========== MILVUS TIMING ==========");

                    System.out.printf("Milvus search time: %.2f seconds%n", elapsedSeconds);

                    System.out.println("======================================");
                })
                .log("Milvus similarity search completed")
                .log("Milvus result: ${body}");
    }


    /*
     * =============================================================
     * MILVUS SEARCH PARAMETERS
     * =============================================================
     */

    public SearchSimpleParam createSearchParam(List<Float> queryVector) {
        return SearchSimpleParam.newBuilder().withCollectionName(COLLECTION_NAME).withVectors(List.of(queryVector))

                /*
                 * Return top 5 results.
                 */

                .withLimit(3L)
                /*
                 * Return these fields from Milvus.
                 */

                .withOutputFields(Arrays.asList("id", "incidentDate", "serviceName", "operation", "status", "exception", "logs")).build();
    }
}