package com.example.AI.processor;

import io.milvus.exception.ServerException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

@Component
public class GlobalExceptionProcessor implements Processor {

    @Override
    public void process(Exchange exchange) {

        Exception exception = exchange.getProperty(
                Exchange.EXCEPTION_CAUGHT,
                Exception.class
        );

        String message = exception != null
                ? exception.getMessage()
                : "Unknown error";

        System.out.println("message >>>"+message);

        // Default response
        int statusCode = 500;
        String responseMessage =
                "An unexpected error occurred while searching historical incidents.";

        // Milvus collection not found
        if (message != null &&
                message.toLowerCase().contains("can't find collection")) {

            statusCode = 404;

            responseMessage =
                    "Historical incidents collection does not exist. " +
                            "Please initialize Milvus before searching.";

        }
        // Other Milvus exceptions
        else if (exception != null &&
                exception.getClass().getName().startsWith("io.milvus")) {

            statusCode = 500;

            responseMessage =
                    "Unable to search historical incidents in Milvus.";
        }

        exchange.getMessage().setHeader(
                Exchange.HTTP_RESPONSE_CODE,
                statusCode
        );

        exchange.getMessage().setHeader(
                Exchange.CONTENT_TYPE,
                "application/json"
        );

        exchange.getMessage().setBody(
                "{\"status\":\"ERROR\"," +
                        "\"message\":\"" + responseMessage + "\"}"
        );
    }
}