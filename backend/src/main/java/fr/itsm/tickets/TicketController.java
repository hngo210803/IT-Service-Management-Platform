package fr.itsm.tickets;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {
    private final TicketService tickets;

    public TicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    @GetMapping
    public List<Ticket> list() {
        return tickets.list();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Ticket> find(@PathVariable long id) {
        return ResponseEntity.of(tickets.find(id));
    }

    @PostMapping
    public ResponseEntity<Ticket> create(@Valid @RequestBody CreateTicket request) {
        Ticket ticket = tickets.create(request.title(), request.description());
        return ResponseEntity.created(URI.create("/api/tickets/" + ticket.getId())).body(ticket);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Ticket> update(@PathVariable long id,
                                         @Valid @RequestBody UpdateStatus request) {
        return ResponseEntity.of(tickets.updateStatus(id, request.status()));
    }

    public record CreateTicket(@NotBlank @Size(max = 120) String title,
                               @NotBlank @Size(max = 3000) String description) {}
    public record UpdateStatus(@NotNull TicketStatus status) {}
}
