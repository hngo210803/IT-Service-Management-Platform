package fr.itsm.assistant;

import fr.itsm.tickets.Ticket;
import fr.itsm.tickets.TicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/mcp")
public class McpController {
    static final String VERSION = "2025-03-26";
    static final String TOOL_NAME = "get_ticket_status";

    private final TicketService tickets;

    public McpController(TicketService tickets) {
        this.tickets = tickets;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> exchange(@RequestBody JsonNode request,
            @RequestHeader(value = "Origin", required = false) String origin) {
        // Le frontend passe par /api. Les requêtes MCP avec un en-tête Origin sont refusées.
        if (origin != null) {
            return ResponseEntity.status(403).build();
        }

        JsonNode id = request.path("id");
        if (!request.path("jsonrpc").asText().equals("2.0")
                || !request.path("method").isString()
                || !(id.isMissingNode() || id.isString() || id.isIntegralNumber())) {
            var error = new LinkedHashMap<String, Object>();
            error.put("jsonrpc", "2.0");
            error.put("id", null);
            error.put("error", Map.of("code", -32600, "message", "Invalid Request"));
            return ResponseEntity.badRequest().body(error);
        }

        if (id.isMissingNode()) {
            return ResponseEntity.accepted().build();
        }

        return switch (request.path("method").asText()) {
            case "initialize" -> result(id, Map.of(
                    "protocolVersion", VERSION,
                    "capabilities", Map.of("tools", Map.of()),
                    "serverInfo", Map.of("name", "itsm-tickets", "version", "0.1.0")));
            case "ping" -> result(id, Map.of());
            case "tools/list" -> result(id, Map.of("tools", List.of(toolDefinition())));
            case "tools/call" -> call(id, request.path("params"));
            default -> error(id, -32601, "Method not found");
        };
    }

    private static Map<String, Object> toolDefinition() {
        return Map.of("name", TOOL_NAME,
                "description", "Lit le statut actuel d'un ticket ITSM par son identifiant numérique.",
                "inputSchema", Map.of("type", "object",
                        "properties", Map.of("id", Map.of("type", "integer", "minimum", 1)),
                        "required", List.of("id"),
                        "additionalProperties", false));
    }

    private ResponseEntity<?> call(JsonNode requestId, JsonNode params) {
        if (!params.path("name").asText().equals(TOOL_NAME)) {
            return error(requestId, -32602, "Unknown tool");
        }

        JsonNode arguments = params.path("arguments");
        JsonNode ticketId = arguments.path("id");
        if (arguments.size() != 1 || !ticketId.isIntegralNumber()
                || !ticketId.canConvertToLong() || ticketId.asLong() < 1) {
            return result(requestId, toolText("Un numéro de ticket entier positif est requis.", true));
        }

        long id = ticketId.asLong();
        Optional<Ticket> ticket = tickets.find(id);
        if (ticket.isEmpty()) {
            return result(requestId, toolText("Le ticket #" + id + " est introuvable.", true));
        }
        return result(requestId, toolText("Ticket #" + id + " | statut : " + ticket.get().getStatus(), false));
    }

    private static Map<String, Object> toolText(String text, boolean error) {
        return Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", error);
    }

    private static ResponseEntity<?> result(JsonNode id, Object value) {
        return ResponseEntity.ok(Map.of("jsonrpc", "2.0", "id", id, "result", value));
    }

    private static ResponseEntity<?> error(JsonNode id, int code, String message) {
        return ResponseEntity.ok(Map.of("jsonrpc", "2.0", "id", id,
                "error", Map.of("code", code, "message", message)));
    }
}
