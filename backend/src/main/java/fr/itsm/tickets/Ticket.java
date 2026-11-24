package fr.itsm.tickets;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tickets")
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 3000)
    private String description;

    // Stocker le nom du statut évite de dépendre de l'ordre des valeurs de l'enum.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Ticket() {
        // Constructeur utilisé par JPA.
    }

    public Ticket(String title, String description) {
        this.title = title;
        this.description = description;
        this.status = TicketStatus.OPEN;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TicketStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    void changeStatus(TicketStatus status) {
        this.status = status;
    }
}
