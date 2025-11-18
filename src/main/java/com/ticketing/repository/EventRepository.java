package com.ticketing.repository;

import com.ticketing.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    
    /**
     * Find events that have at least one available ticket.
     * Uses INNER JOIN for better performance than EXISTS subquery.
     * 
     * Removed old query that used denormalized availableTickets field.
     * Now queries tickets table directly to avoid inconsistency.
     */
    @Query("""
        SELECT DISTINCT e FROM Event e 
        INNER JOIN Ticket t ON t.event.id = e.id 
        WHERE t.status = 'AVAILABLE'
    """)
    List<Event> findEventsWithAvailableTickets();
    
    // Removed: incrementAvailableTickets() - no longer needed
    // availableTickets is now calculated, not persisted

    // findAll() and findAll(Pageable) are provided by JpaRepository
    // They use lazy loading for tickets (which have @JsonIgnore anyway)
}
