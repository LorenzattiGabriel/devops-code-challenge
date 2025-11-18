package com.ticketing.controller;

import com.ticketing.model.Event;
import com.ticketing.service.EventService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@Validated
@Tag(name = "Events", description = "Event management")
public class EventController {
    
    private final EventService eventService;
    
    public EventController(EventService eventService) {
        this.eventService = eventService;
    }
    
    /**
     * GET /api/v1/events
     * Returns: 200 OK with list of events ( lazy loading)
     * Note: For large datasets, use /api/v1/events/paged instead
     */
    @GetMapping
    public ResponseEntity<List<Event>> getAllEvents() {
        List<Event> events = eventService.getAllEvents();
        return ResponseEntity.ok(events);
    }
    
    /**
     * GET /api/v1/events/paged?page=0&size=20&sort=id,desc
     * Returns: 200 OK with paginated events
     */
    @GetMapping("/paged")
    public ResponseEntity<Page<Event>> getAllEventsPaged(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        
        Page<Event> events = eventService.getAllEventsPaged(pageable);
        return ResponseEntity.ok(events);
    }
    
    /**
     * GET /api/v1/events/{id}
     * Returns: 200 OK if found
     *          400 BAD REQUEST if id is invalid
     *          404 NOT FOUND if event doesn't exist
     */
    @GetMapping("/{id}")
    public ResponseEntity<Event> getEvent(
            @Parameter(description = "Event ID", example = "1")
            @PathVariable
            @Positive(message = "Event ID must be a positive number")
            Long id) {
        
        Event event = eventService.getEventById(id);
        return ResponseEntity.ok(event);
    }
    
    /**
     * GET /api/v1/events/available
     * Returns: 200 OK with list of events that have available tickets
     */
    @GetMapping("/available")
    public ResponseEntity<List<Event>> getEventsWithAvailableTickets() {
        List<Event> events = eventService.getEventsWithAvailableTickets();
        return ResponseEntity.ok(events);
    }
    
    /**
     * POST /api/v1/events
     * Returns: 201 CREATED with created event
     *          400 BAD REQUEST if validation fails
     * 
     * Bean Validations:
     * - name: 3-100 chars, required (@NotBlank, @Size)
     * - venue: 3-255 chars, required (@NotBlank, @Size)
     * - eventDate: future date, required (@NotNull, @Future)
     * - totalTickets: min 1, required (@NotNull, @Min)
     */
    @PostMapping
    public ResponseEntity<Event> createEvent(@Valid @RequestBody Event event) {
        Event createdEvent = eventService.createEvent(event);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdEvent);
    }
}
