package company.policy;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PolicyAssistant {
    private interface Assistant {
        @SystemMessage("""
                You are a company policy assistant. Answer only from the retrieved company policy content.
                If the answer is not present in the retrieved content, say: The requested information is not available in the company policy documents.
                """)
        String answer(String question);
    }

    public static void main(String[] args) throws IOException {
        requireApiKey();
        EmbeddingModel embeddings = OpenAiEmbeddingModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(System.getenv().getOrDefault("OPENAI_EMBEDDING_MODEL", "text-embedding-3-small"))
                .build();
        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddings)
                .embeddingStore(store)
                .embeddingInputType(EmbeddingInputType.DOCUMENT)
                .documentSplitter(doc -> {
                    List<TextSegment> segments = new ArrayList<>();
                    Matcher matcher = Pattern.compile("(?<=[.!?])\\s+|\\n\\n+").matcher(doc.text());
                    int start = 0;
                    while (matcher.find()) {
                        addIfUseful(doc.metadata(), doc.text().substring(start, matcher.end()).trim(), segments);
                        start = matcher.end();
                    }
                    addIfUseful(doc.metadata(), doc.text().substring(start).trim(), segments);
                    return segments;
                })
                .build();

        List<Document> documents = new ArrayList<>();
        try (var paths = Files.list(Path.of("src/main/resources/documents"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".md")).sorted().toList()) {
                documents.add(Document.from(Files.readString(path),
                        new Metadata(Map.of("file_name", path.getFileName().toString()))));
            }
        }
        ingestor.ingest(documents);
        System.out.println("Indexed " + documents.size() + " policy documents.");

        var retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddings)
                .embeddingInputType(EmbeddingInputType.QUERY)
                .maxResults(3)
                .minScore(0.15)
                .build();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(6))
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder().contentRetriever(retriever).build())
                .build();

        Scanner scanner = new Scanner(System.in);
        System.out.println("Ask a company policy question, or type 'exit' to quit.");
        while (true) {
            System.out.print("Policy question> ");
            String question = scanner.nextLine().trim();
            if (question.equalsIgnoreCase("exit")) break;
            if (!question.isEmpty()) System.out.println(assistant.answer(question));
        }
    }

    private static ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini"))
                .temperature(0.0)
                .build();
    }

    private static void addIfUseful(Metadata metadata, String text, List<TextSegment> segments) {
        if (!text.isEmpty()) segments.add(TextSegment.from(text, metadata));
    }

    private static void requireApiKey() {
        if (System.getenv("OPENAI_API_KEY") == null || System.getenv("OPENAI_API_KEY").isBlank()) {
            throw new IllegalStateException("Set OPENAI_API_KEY before running the assistant.");
        }
    }
}
