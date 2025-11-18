package com.ticketing.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;
    
    private String customerEmail;
    private String status; // AVAILABLE, RESERVED, SOLD
    private LocalDateTime reservedUntil;
    private LocalDateTime createdAt;
    
    public Ticket(Event event, String status) {
        this.event = event;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }
}
