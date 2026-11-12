# Company Policy Assistant

A small Java application that answers employee questions using LangChain4j, OpenAI embeddings, an in-memory vector store, and retrieval-augmented generation (RAG).

## Prerequisites

- Java 21
- Maven 3.9 or newer
- An OpenAI API key with access to the configured chat and embedding models

## Configuration

Set the API key in the terminal before running:

```bash
export OPENAI_API_KEY=your-openai-api-key
```

Optional model overrides:

```bash
export OPENAI_MODEL=gpt-4o-mini
export OPENAI_EMBEDDING_MODEL=text-embedding-3-small
```

Do not commit the API key or place it in `pom.xml`, source code, or this README.

## Build

```bash
mvn clean package
```

## Run

From the project root:

```bash
mvn exec:java
```

The application loads the Markdown files in `src/main/resources/documents`, splits them into text segments, generates embeddings, stores them in an `InMemoryEmbeddingStore`, retrieves the most relevant segments, and sends them to the chat model as context.

Example questions:

```text
How many annual leave days do employees get?
How many days can employees work from home each week?
What medical insurance benefit do employees receive?
```

Type `exit` to quit the interactive console.

If the policy documents do not contain the answer, the assistant is instructed to say that the information is not available.

## Project layout

- `src/main/java/company/policy/PolicyAssistant.java` - ingestion, retrieval, AI service, and console loop
- `src/main/resources/documents/` - sample policy documents
- `pom.xml` - Java and LangChain4j dependencies
