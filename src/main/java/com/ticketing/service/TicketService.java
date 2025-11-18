package com.ticketing.service;

import com.ticketing.exception.InvalidRequestException;
import com.ticketing.exception.NoTicketsAvailableException;
import com.ticketing.exception.ResourceNotFoundException;
import com.ticketing.model.Event;
import com.ticketing.model.Ticket;
import com.ticketing.repository.EventRepository;
import com.ticketing.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class TicketService {
    
    private static final Logger log = LoggerFactory.getLogger(TicketService.class);
    private static final int RESERVATION_MINUTES = 10;
    
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final DistributedLockService lockService;
    
    public TicketService(TicketRepository ticketRepository, 
                         EventRepository eventRepository, 
                         DistributedLockService lockService) {
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
        this.lockService = lockService;
    }
    
    public List<Ticket> getAvailableTickets(Long eventId) {
        // Verify event exists
        eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));
        
        return ticketRepository.findAvailableByEventId(eventId);
    }
    
    /**
     * Reserve a ticket for an event with distributed locking to prevent race conditions.
     * This method works across multiple application instances.
     * 
     * @param eventId ID of the event (validated by Bean Validation)
     * @param customerEmail Email of the customer (validated by Bean Validation)
     * @return Reserved ticket
     * @throws NoTicketsAvailableException if no tickets available
     * @throws ResourceNotFoundException if event doesn't exist
     */
    public Ticket reserveTicket(Long eventId, String customerEmail) {
        // Verify event exists (business rule validation)
        eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));
        
        String lockKey = "ticket:reserve:event:" + eventId;
        
        log.info("Attempting to reserve ticket for event {} by customer {}", eventId, customerEmail);
        
        return lockService.executeWithLock(
            lockKey,
            3,  // Wait up to 3 seconds to acquire lock
            10, // Hold lock for max 10 seconds
            TimeUnit.SECONDS,
            () -> reserve(eventId, customerEmail)
        );
    }
    
    @Transactional
    @CacheEvict(value = {"available-tickets-count", "events", "events-list", "events-paged", "available-events"}, allEntries = true)
    protected Ticket reserve(Long eventId, String customerEmail) {
        Ticket ticket = ticketRepository.findFirstAvailableWithLock(eventId)
                .orElseThrow(() -> {
                    log.warn("No tickets available for event {}", eventId);
                    return new NoTicketsAvailableException(eventId);
                });
        
        // Reserve the ticket
        ticket.setStatus("RESERVED");
        ticket.setCustomerEmail(customerEmail);
        ticket.setReservedUntil(LocalDateTime.now().plusMinutes(RESERVATION_MINUTES));
        
        // No need to update event.availableTickets (now calculated from tickets table)
        // Cache eviction ensures clients see updated count on next request
        
        Ticket savedTicket = ticketRepository.save(ticket);
        log.info("Ticket {} reserved successfully for customer {} on event {}",
                savedTicket.getId(), customerEmail, eventId);
        
        return savedTicket;
    }
    
    public List<Ticket> getCustomerTickets(String email) {
        return ticketRepository.findByCustomerEmail(email);
    }
    
    /**
     * Cleanup expired reservations and make tickets available again.
     * 
     * Performance optimizations:
     * 1. Database-level filtering: Only loads expired tickets (not all tickets)
     * 2. Batch update: Updates all tickets in single query using saveAll()
     * 3. No event updates needed: availableTickets calculated from tickets table
     * 
     * Before (with denormalized availableTickets):
     * - 100 expired tickets → 200 UPDATE queries (tickets + events)
     * 
     * After (with calculated availableTickets):
     * - 100 expired tickets → 1 batch UPDATE query (tickets only)
     * - 99.5% reduction in database writes!
     * 
     * Cache eviction: Invalidates caches so clients see updated counts.
     * 
     * Scheduled execution: Runs automatically every 5 minutes (300,000 ms)
     * Initial delay: 60 seconds to allow application to fully start
     */
    @org.springframework.scheduling.annotation.Scheduled(
        fixedDelay = 300000,      // Run every 5 minutes
        initialDelay = 60000      // Wait 1 minute after startup
    )
    @Transactional
    @CacheEvict(value = {"available-tickets-count", "events", "events-list", "events-paged", "available-events"}, allEntries = true)
    public void cleanupExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();
        log.debug("Starting cleanup of expired reservations before {}", now);

        List<Ticket> expiredTickets = ticketRepository.findExpiredReservations(now);
        
        if (expiredTickets.isEmpty()) {
            log.debug("No expired reservations found");
            return;
        }
        
        log.info("Found {} expired reservations to cleanup", expiredTickets.size());

        // Update tickets (mark as AVAILABLE) in single batch
        expiredTickets.forEach(ticket -> {
            ticket.setStatus("AVAILABLE");
            ticket.setCustomerEmail(null);
            ticket.setReservedUntil(null);
        });
        ticketRepository.saveAll(expiredTickets);
        
        // No need to update events table!
        // availableTickets is now calculated from tickets table
        // Cache eviction ensures clients see updated counts on next request
        
        log.info("Cleanup completed: {} tickets released", expiredTickets.size());
    }
}
