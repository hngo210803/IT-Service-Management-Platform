package fr.itsm.tickets;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
public class TicketService {
    private final TicketRepository repository;

    public TicketService(TicketRepository repository) {
        this.repository = repository;
    }

    public List<Ticket> list() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    public Optional<Ticket> find(long id) {
        return repository.findById(id);
    }

    public Ticket create(String title, String description) {
        return repository.save(new Ticket(title.strip(), description.strip()));
    }

    @Transactional
    public Optional<Ticket> updateStatus(long id, TicketStatus status) {
        // JPA enregistre la modification de l'entité à la fin de la transaction.
        return repository.findById(id).map(ticket -> {
            ticket.changeStatus(status);
            return ticket;
        });
    }
}
