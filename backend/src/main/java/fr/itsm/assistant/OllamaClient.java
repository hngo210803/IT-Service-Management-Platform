package fr.itsm.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OllamaClient {
    private final RestClient client;
    private final String chatModel;
    private final String embeddingModel;

    public OllamaClient(@Value("${itsm.ollama-url}") String url,
                        @Value("${itsm.chat-model}") String chatModel,
                        @Value("${itsm.embedding-model}") String embeddingModel) {
        var timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(Duration.ofSeconds(120));
        this.client = RestClient.builder().baseUrl(url).requestFactory(timeouts).build();
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    public JsonNode chooseTools(List<Object> messages, List<Object> tools) {
        return chat(Map.of(
                "model", chatModel, "stream", false, "think", false,
                "messages", messages, "tools", tools,
                "options", Map.of("temperature", 0, "num_predict", 180))).path("tool_calls");
    }

    public String writeAnswer(List<Object> messages) {
        return chat(Map.of(
                "model", chatModel, "stream", false, "think", false,
                "messages", messages,
                "format", Map.of("type", "object", "required", List.of("answer", "sourceIds"),
                        "properties", Map.of(
                                "answer", Map.of("type", "string"),
                                "sourceIds", Map.of("type", "array", "items", Map.of("type", "string"))),
                        "additionalProperties", false),
                "options", Map.of("temperature", 0, "num_predict", 200))).path("content").asText();
    }

    private JsonNode chat(Map<String, Object> payload) {
        JsonNode message = post("/api/chat", payload).path("message");
        if (!message.isObject()) {
            throw new RestClientException("Ollama did not return a message");
        }
        return message;
    }

    public List<double[]> embed(List<String> texts) {
        JsonNode embeddings = post("/api/embed", Map.of("model", embeddingModel, "input", texts))
                .path("embeddings");
        if (!embeddings.isArray() || embeddings.size() != texts.size()) {
            throw new RestClientException("Unexpected number of embeddings");
        }

        var vectors = new ArrayList<double[]>();
        for (JsonNode embedding : embeddings) {
            if (!embedding.isArray() || embedding.isEmpty()) {
                throw new RestClientException("Invalid embedding");
            }
            double[] vector = new double[embedding.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = embedding.get(i).asDouble();
            }
            vectors.add(vector);
        }
        return vectors;
    }

    private JsonNode post(String path, Object payload) {
        JsonNode response = client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .body(payload).retrieve().body(JsonNode.class);
        if (response == null || response.has("error")) {
            throw new RestClientException("Invalid Ollama response to " + path);
        }
        return response;
    }
}
