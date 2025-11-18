package com.ticketing.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Event name is required")
    @Size(min = 3, max = 100, message = "Event name must be between 3 and 100 characters")
    private String name;
    
    @NotBlank(message = "Venue is required")
    @Size(min = 3, max = 255, message = "Venue must be between 3 and 255 characters")
    private String venue;
    
    @NotNull(message = "Event date is required")
    @Future(message = "Event date must be in the future")
    private LocalDateTime eventDate;
    
    @NotNull(message = "Total tickets is required")
    @Min(value = 1, message = "Total tickets must be at least 1")
    private Integer totalTickets;
    
    // Removed: availableTickets field (denormalized data prone to inconsistency)
    // Now calculated in real-time from tickets table (single source of truth)
    
    @JsonIgnore
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Ticket> tickets;

    @Transient
    private Integer availableTickets;

    public Event(String name, String venue, LocalDateTime eventDate, Integer totalTickets) {
        this.name = name;
        this.venue = venue;
        this.eventDate = eventDate;
        this.totalTickets = totalTickets;
    }
}
