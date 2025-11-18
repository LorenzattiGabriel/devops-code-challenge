package com.ticketing.controller;

import com.ticketing.model.Ticket;
import com.ticketing.service.TicketService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
@Validated
@Tag(name = "Tickets", description = "Ticket management")
public class TicketController {
    
    private final TicketService ticketService;
    
    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }
    
    /**
     * GET /api/v1/tickets/event/{eventId}
     * Returns: 200 OK with list of available tickets
     *          400 BAD REQUEST if eventId is invalid
     *          404 NOT FOUND if event doesn't exist
     */
    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<Ticket>> getAvailableTickets(
            @Parameter(description = "Event ID", example = "1")
            @PathVariable
            @Positive(message = "Event ID must be a positive number")
            Long eventId) {
        
        List<Ticket> tickets = ticketService.getAvailableTickets(eventId);
        return ResponseEntity.ok(tickets);
    }
    
    /**
     * POST /api/v1/tickets/reserve
     * Returns: 201 CREATED with reserved ticket
     *          400 BAD REQUEST if parameters are invalid
     *          404 NOT FOUND if event doesn't exist
     *          409 CONFLICT if no tickets available
     */
    @PostMapping("/reserve")
    public ResponseEntity<Ticket> reserveTicket(
            @Parameter(description = "Event ID", required = true)
            @RequestParam
            @Positive(message = "Event ID must be positive")
            Long eventId,
            
            @Parameter(description = "Customer email", required = true)
            @RequestParam
            @NotBlank(message = "Email is required")
            @Email(message = "Invalid email format")
            String customerEmail) {
        
        Ticket ticket = ticketService.reserveTicket(eventId, customerEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(ticket);
    }
    
    /**
     * GET /api/v1/tickets/customer/{email}
     * Returns: 200 OK with list of customer's tickets
     *          400 BAD REQUEST if email is invalid
     */
    @GetMapping("/customer/{email}")
    public ResponseEntity<List<Ticket>> getCustomerTickets(
            @Parameter(description = "Customer email", example = "user@example.com")
            @PathVariable
            @NotBlank(message = "Email is required")
            @Email(
                message = "Invalid email format",
                regexp = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
            )
            String email) {
        
        List<Ticket> tickets = ticketService.getCustomerTickets(email);
        return ResponseEntity.ok(tickets);
    }
}
