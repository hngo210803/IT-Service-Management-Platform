package fr.itsm.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private static final Logger LOG = LoggerFactory.getLogger(AssistantController.class);
    private final AssistantService assistant;

    public AssistantController(AssistantService assistant) {
        this.assistant = assistant;
    }

    @PostMapping
    public AssistantService.Answer ask(@Valid @RequestBody Question request) {
        try {
            return assistant.ask(request.question().strip());
        } catch (RestClientException error) {
            LOG.warn("Assistant indisponible : {}", error.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Assistant indisponible");
        }
    }

    public record Question(@NotBlank @Size(max = 1000) String question) {}
}
