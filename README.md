# camel-agentic-rag

Agentic RAG with Apache Camel, LangChain4j, Ollama, Milvus and Tavily.

An AI agent (`qwen3:8b` running locally in Ollama) can search historical production incidents stored in Milvus and search the public web through Tavily, then combine the results into one answer.

This README only covers how to run the application. Milvus runs in Docker, and the two Ollama models run locally.

## Prerequisites

- Java 19 (the version set in `pom.xml`)
- Docker and Docker Compose
- [Ollama](https://ollama.com)
- A [Tavily](https://tavily.com) API key
- `curl`

The project includes the Maven wrapper, so a separate Maven installation is not required.

## 1. Start Milvus with Docker

Download the Milvus standalone Docker Compose file:

```bash
curl -L https://github.com/milvus-io/milvus/releases/download/v3.0.0/milvus-standalone-docker-compose.yml -o docker-compose.yml
```

Start the Milvus containers:

```bash
docker compose up -d
```

Verify that they are running:

```bash
docker compose ps
```

Milvus can take a short while to become healthy on the first start. The application connects to it on `localhost:19530`.

## 2. Pull the Ollama models

Both models run locally through Ollama (`http://localhost:11434`):

```bash
ollama pull qwen3:8b
ollama pull nomic-embed-text
```

- `qwen3:8b` is the chat model used by the agent.
- `nomic-embed-text` is the embedding model (768 dimensions), used to create the vectors stored in Milvus.

Check that both models are available:

```bash
ollama list
```

## 3. Configure the application

Open `src/main/resources/application.properties` and set these values:

```properties
# Folder that contains daily_status_report_with_date.csv
daily.report.filepath=file:///path/to/data/input
daily.report.errorfilepath=file:///path/to/data/error

# Milvus
camel.component.milvus.host=localhost
camel.component.milvus.port=19530

# Ollama
langchain4j.ollama.embedding-model.base-url=http://localhost:11434
langchain4j.ollama.embedding-model.model-name=nomic-embed-text
camel.component.openai.base-url=http://localhost:11434/v1
camel.component.openai.model=qwen3:8b

# Tavily web search
tavily.api.url=https://api.tavily.com/search
tavily.api.key=<your-tavily-api-key>
```

Do not commit your real Tavily API key. A safer option is to leave it out of the file and set it as an environment variable before you start the application:

```bash
export TAVILY_API_KEY=<your-tavily-api-key>
```

## 4. Add the incident CSV file

Download the sample file [`daily_status_report_with_date.csv`](sample-data/daily_status_report_with_date.csv) from this repository and copy it into the folder you set in `daily.report.filepath`.

The file has these columns: `IncidentDate`, `ServiceName`, `Operation`, `Status`, `ExceptionClass` and `LogSnippet`. The sample file contains this data:

```csv
IncidentDate,ServiceName,Operation,Status,ExceptionClass,LogSnippet
2026-09-04,loan-origination-service,CreateTransactionAPI,FAILED,FileNotFoundException,FileNotFoundException: Input file missing
2026-09-04,loan-origination-service,GenerateReport,FAILED,SocketTimeoutException,SocketTimeoutException: Read timed out
2026-09-04,product-service,GenerateReport,FAILED,CannotGetJdbcConnectionException,CannotGetJdbcConnectionException: Unable to obtain JDBC Connection
2026-09-04,payment-service,FileUpload,FAILED,SocketTimeoutException,SocketTimeoutException: Read timed out
2026-09-04,payment-service,GenerateReport,FAILED,SocketTimeoutException,SocketTimeoutException: Read timed out
2026-09-04,customer-api,DailyBatch,FAILED,NullPointerException,NullPointerException: Null reference
```

Only rows with the status `FAILED` are embedded and inserted into the Milvus vector database. Other rows are skipped.

## 5. Run the application

From the project root:

```bash
./mvnw spring-boot:run
```

You can also run `CamelAgenticRagApplication` from your IDE. The application starts on `http://localhost:8080`.

## 6. Create the Milvus collection

Create the `historical_incidents` collection, create its vector index and load it:

```bash
curl -X POST "http://localhost:8080/api/milvus/initialize"
```

A successful call returns:

```json
{"status":"SUCCESS","message":"Milvus initialized successfully"}
```

## 7. Load the incident history

The file route reads `daily_status_report_with_date.csv` from the folder you configured, creates an embedding for each `FAILED` incident and inserts it into Milvus. Make sure the CSV is in place and the application is running. Watch the application log for `Incident inserted into Milvus successfully`.

Camel moves processed files into a `.camel` sub-folder by default. If you need to load the file again, move it back to the input folder.

## 8. Verify the stored incidents

```bash
curl "http://localhost:8080/api/milvus/incidents"
```

This returns the incidents stored in Milvus.

## 9. Test the application

Search the historical incidents directly:

```bash
curl -X POST "http://localhost:8080/incidents/search" \
  -H "Content-Type: application/json" \
  -d '{"question":"Why did the product-service fail?"}'
```

Ask the agent, which can use both the incident search and the web search:

```bash
curl -X POST "http://localhost:8080/incidents/agent" \
  -H "Content-Type: application/json" \
  -d '{"question":"Why did the product-service fail and how to resolve this?"}'
```

## Re-running everything from scratch

Use these steps to rebuild the collection, for example after changing the data or the embedding model.

1. Drop the old collection:

   ```bash
   curl --request POST "http://localhost:19530/v2/vectordb/collections/drop" \
     -H "Authorization: Bearer root:Milvus" \
     -H "Content-Type: application/json" \
     -d '{"collectionName":"historical_incidents"}'
   ```

2. Restart the Camel / Spring Boot application.
3. Re-create and load the collection:

   ```bash
   curl -X POST "http://localhost:8080/api/milvus/initialize"
   ```

4. Run the CSV ingestion again by putting `daily_status_report_with_date.csv` back in the input folder.
5. Verify the history:

   ```bash
   curl "http://localhost:8080/api/milvus/incidents"
   ```

6. Test the search:

   ```bash
   curl -X POST "http://localhost:8080/incidents/search" \
     -H "Content-Type: application/json" \
     -d '{"question":"Why did the product-service fail?"}'
   ```

## Stopping everything

```bash
docker compose stop     # stop Milvus and keep its data
docker compose down     # remove the Milvus containers
```

Stop the Spring Boot application with `Ctrl+C`.
