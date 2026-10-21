package com.example.AI.route;

import io.milvus.grpc.DataType;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.index.CreateIndexParam;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milvus.Milvus;
import org.apache.camel.component.milvus.MilvusAction;
import org.apache.camel.component.milvus.MilvusEndpoint;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class MilvusAdminRoute extends RouteBuilder {

    private static final String COLLECTION_NAME = "historical_incidents";
    private static final int EMBEDDING_DIMENSION = 768;

    @Override
    public void configure() {

        restConfiguration().component("servlet").bindingMode(RestBindingMode.json);


        /*
         * =====================================================
         * MILVUS INITIALIZATION API
         *
         * POST /api/milvus/initialize
         * =====================================================
         */

        rest("/api/milvus")
                .post("/initialize")
                .produces("application/json")
                .to("direct:initialize-milvus");


        /*
         * =====================================================
         * QUERY INCIDENTS API
         *
         * GET /api/milvus/incidents
         * =====================================================
         */

        rest("/api/milvus")
                .get("/incidents")
                .produces("application/json")
                .to("direct:query-historical-incidents");


        /*
         * =====================================================
         * INITIALIZE MILVUS
         * =====================================================
         */

        from("direct:initialize-milvus")
                .routeId("initialize-milvus-api")

                .log("Starting Milvus initialization")

                .to("direct:create-milvus-collection")

                .log("Collection creation completed")

                .to("direct:create-milvus-index")

                .log("Index creation completed")

                .to("direct:load-milvus-collection")

                .log("Collection loading completed")

                .setBody(constant(
                        "{\"status\":\"SUCCESS\",\"message\":\"Milvus initialized successfully\"}"
                ));


        /*
         * =====================================================
         * CREATE COLLECTION
         * =====================================================
         */

        from("direct:create-milvus-collection")
                .routeId("create-milvus-collection")

                .setHeader(Milvus.Headers.ACTION)
                .constant(MilvusAction.CREATE_COLLECTION)

                .setBody()
                .method(this, "createCollectionParam")

                .to("milvus:" + COLLECTION_NAME)

                .log("Milvus collection created successfully");


        /*
         * =====================================================
         * CREATE VECTOR INDEX
         * =====================================================
         */

        from("direct:create-milvus-index")
                .routeId("create-milvus-index")

                .setHeader(Milvus.Headers.ACTION)
                .constant(MilvusAction.CREATE_INDEX)

                .setBody()
                .method(this, "createEmbeddingIndexParam")

                .to("milvus:" + COLLECTION_NAME)

                .log("Milvus embedding index created successfully");


        /*
         * =====================================================
         * LOAD COLLECTION
         * =====================================================
         */

        from("direct:load-milvus-collection")
                .routeId("load-milvus-collection")

                .process(exchange -> loadCollection())

                .log("Milvus collection loaded successfully");


        /*
         * =====================================================
         * QUERY HISTORICAL INCIDENTS
         *
         * GET /api/milvus/incidents
         * =====================================================
         */

        from("direct:query-historical-incidents")
                .routeId("query-historical-incidents")

                .setHeader(Milvus.Headers.ACTION)
                .constant(MilvusAction.QUERY)

                .setBody()
                .method(this, "createQueryParam")

                .to("milvus:" + COLLECTION_NAME)

                .log("Milvus query result: ${body}");
    }


    /*
     * =============================================================
     * CREATE COLLECTION PARAMETER
     * =============================================================
     */

    public CreateCollectionParam createCollectionParam() {

        /*
         * 1. ID
         */

        FieldType idField = FieldType.newBuilder()
                        .withName("id")
                        .withDescription("Incident ID")
                        .withDataType(DataType.Int64)
                        .withPrimaryKey(true)
                        .withAutoID(true)
                        .build();


        /*
         * 2. Incident Date
         */

        FieldType incidentDateField = FieldType.newBuilder()
                        .withName("incidentDate")
                        .withDescription("Date when incident occurred")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(50)
                        .build();


        /*
         * 3. Service Name
         */

        FieldType serviceNameField = FieldType.newBuilder()
                        .withName("serviceName")
                        .withDescription("Service which generated the incident")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(200)
                        .build();


        /*
         * 4. Operation
         */

        FieldType operationField = FieldType.newBuilder()
                        .withName("operation")
                        .withDescription("Operation being performed")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(200)
                        .build();


        /*
         * 5. Status
         */

        FieldType statusField = FieldType.newBuilder()
                        .withName("status")
                        .withDescription("Incident status")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(50)
                        .build();


        /*
         * 6. Exception
         */

        FieldType exceptionField = FieldType.newBuilder()
                        .withName("exception")
                        .withDescription("Exception associated with incident")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(500)
                        .build();


        /*
         * 7. Logs
         */

        FieldType logsField = FieldType.newBuilder()
                        .withName("logs")
                        .withDescription("Relevant application logs")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(5000)
                        .build();


        /*
         * 8. Embedding
         */

        FieldType embeddingField = FieldType.newBuilder()
                        .withName("embedding")
                        .withDescription("768 dimensional incident embedding")
                        .withDataType(DataType.FloatVector)
                        .withDimension(EMBEDDING_DIMENSION)
                        .build();


        return CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withDescription("Historical failed incidents")
                .withShardsNum(2)
                .addFieldType(idField)
                .addFieldType(incidentDateField)
                .addFieldType(serviceNameField)
                .addFieldType(operationField)
                .addFieldType(statusField)
                .addFieldType(exceptionField)
                .addFieldType(logsField)
                .addFieldType(embeddingField)
                .build();
    }


    /*
     * =============================================================
     * CREATE VECTOR INDEX PARAMETER
     * =============================================================
     */

    public CreateIndexParam createEmbeddingIndexParam() {

        return CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldName("embedding")
                .withIndexType(IndexType.AUTOINDEX)
                .withMetricType(MetricType.COSINE)
                .build();
    }


    /*
     * =============================================================
     * LOAD COLLECTION
     * =============================================================
     */

    private void loadCollection() {

        MilvusEndpoint endpoint = getContext().getEndpoint("milvus:" + COLLECTION_NAME, MilvusEndpoint.class);
        var response = endpoint.getClient().loadCollection(LoadCollectionParam.newBuilder().withCollectionName(COLLECTION_NAME).build());

        if (response.getStatus() != 0) {
            throw new RuntimeException("Failed to load Milvus collection: " + response.getMessage()
            );
        }

        System.out.println("Milvus loadCollection request successful");
    }


    /*
     * =============================================================
     * QUERY PARAMETERS
     * =============================================================
     *
     * Query all FAILED incidents.
     */

    public QueryParam createQueryParam() {

        return QueryParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withExpr("status == \"FAILED\"")

                .withOutFields(Arrays.asList(
                                "id",
                                "incidentDate",
                                "serviceName",
                                "operation",
                                "status",
                                "exception",
                                "logs"))
                .build();
    }
}