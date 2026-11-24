package fr.itsm.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.Map;

@Component
public class McpClient {
    private final RestClient client;

    public McpClient(@Value("${itsm.mcp-url}") String url) {
        var timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(2));
        timeouts.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(url).requestFactory(timeouts)
                .defaultHeader("Accept", "application/json, text/event-stream")
                .build();
    }

    public JsonNode discoverTicketTool() {
        var init = send(1, "initialize", Map.of(
                "protocolVersion", McpController.VERSION,
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "itsm-assistant", "version", "0.1.0")));
        if (!McpController.VERSION.equals(init.path("protocolVersion").asText())) {
            throw new RestClientException("Unsupported MCP version");
        }
        client.post().contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"))
                .retrieve().toBodilessEntity();

        for (JsonNode tool : send(2, "tools/list", Map.of()).path("tools")) {
            if (McpController.TOOL_NAME.equals(tool.path("name").asText())) {
                return tool;
            }
        }
        throw new RestClientException("Ticket tool missing");
    }

    public String ticketStatus(JsonNode arguments) {
        return send(3, "tools/call", Map.of("name", McpController.TOOL_NAME, "arguments", arguments))
                .path("content").path(0).path("text").asText();
    }

    private JsonNode send(int id, String method, Object params) {
        JsonNode response = client.post().contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params))
                .retrieve().body(JsonNode.class);
        if (response == null || response.path("id").asInt(-1) != id || !response.has("result")) {
            throw new RestClientException("Invalid MCP response to " + method);
        }
        return response.path("result");
    }
}
