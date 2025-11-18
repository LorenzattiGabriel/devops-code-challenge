package com.ticketing.service;

import com.ticketing.exception.ResourceNotFoundException;
import com.ticketing.model.Event;
import com.ticketing.repository.EventRepository;
import com.ticketing.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class EventService {
    
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    
    public EventService(EventRepository eventRepository, TicketRepository ticketRepository) {
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
    }
    
    /**
     * Get all events without loading tickets (lazy loading).
     * Note: availableTickets count is injected from tickets table
     * Consider using getAllEventsPaged() for better performance with large datasets.
     * 
     * Cached with TTL defined in application.properties
     */
    @Cacheable(value = "events-list", unless = "#result.isEmpty()")
    public List<Event> getAllEvents() {
        List<Event> events = eventRepository.findAll();
        countAvailableTicketsByEvent(events);
        return events;
    }
    
    /**
     * Get all events with pagination
     * Cached per page to avoid repeated queries.
     */
    @Cacheable(value = "events-paged", key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<Event> getAllEventsPaged(Pageable pageable) {
        Page<Event> events = eventRepository.findAll(pageable);
        countAvailableTicketsByEvent(events.getContent());
        return events;
    }
    
    /**
     * Get event by ID with Redis cache.
     * Cache key: "events::1", "events::2", etc.
     */
    @Cacheable(value = "events", key = "#id")
    public Event getEventById(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", id));
        event.setAvailableTickets(getAvailableTicketsCount(event.getId()));
        return event;
    }
    
    /**
     * Get events with available tickets.
     * Cached because it's a common query and relatively expensive.
     */
    @Cacheable(value = "available-events", unless = "#result.isEmpty()")
    public List<Event> getEventsWithAvailableTickets() {
        List<Event> events = eventRepository.findEventsWithAvailableTickets();
        countAvailableTicketsByEvent(events);
        return events;
    }
    
    /**
     * Get available tickets count for an event (cached).
     * Single source of truth: queries tickets table directly.
     * 
     * @param eventId Event ID
     * @return Number of AVAILABLE tickets for this event
     */
    @Cacheable(value = "available-tickets-count", key = "#eventId")
    public int getAvailableTicketsCount(Long eventId) {
        return ticketRepository.countAvailableByEventId(eventId);
    }
    
    /**
     * Inject availableTickets count into event entities.
     * Called after fetching events from database to populate the transient field.
     */
    private void countAvailableTicketsByEvent(List<Event> events) {
        events.forEach(event -> 
            event.setAvailableTickets(getAvailableTicketsCount(event.getId()))
        );
    }
    
    /**
     * Create event and evict all event-related caches.
     * This ensures clients see the new event immediately.
     */
    @CacheEvict(value = {"events-list", "events-paged", "available-events"}, allEntries = true)
    public Event createEvent(Event event) {
        return eventRepository.save(event);
    }
}
