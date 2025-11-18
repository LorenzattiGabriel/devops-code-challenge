package com.ticketing.repository;

import com.ticketing.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    
    @Query("SELECT t FROM Ticket t WHERE t.event.id = :eventId AND t.status = 'AVAILABLE'")
    List<Ticket> findAvailableByEventId(Long eventId);
    
    @Query("SELECT t FROM Ticket t WHERE t.customerEmail = :email")
    List<Ticket> findByCustomerEmail(String email);
    
    /**
     * Find first available ticket for reservation.
     * 
     * Note: Database pessimistic lock (@Lock PESSIMISTIC_WRITE) is NOT needed here because:
     * - Redis distributed lock (Redisson) already protects the critical section at application level
     * - Only one instance can execute the reservation logic at a time per event
     * - Adding DB lock would be redundant and add unnecessary overhead
     * - This approach works correctly across multiple application instances
     * 
     * The Redis lock key: "ticket:reserve:event:{eventId}" ensures no race conditions.
     * 
     * Note: Using native query with LIMIT 1 to fetch only one ticket instead of all available tickets.
     */
    @Query(value = "SELECT * FROM tickets WHERE event_id = :eventId AND status = 'AVAILABLE' ORDER BY id LIMIT 1", nativeQuery = true)
    Optional<Ticket> findFirstAvailableWithLock(@Param("eventId") Long eventId);


    /**
     * Find expired reserved tickets using database-level filtering.
     * This is much more efficient than loading all tickets and filtering in Java.
     * 
     * @param now Current timestamp to compare against reservedUntil
     * @return List of expired tickets (status='RESERVED' and reservedUntil < now)
     */
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'RESERVED'
          AND t.reservedUntil < :now
    """)
    List<Ticket> findExpiredReservations(@Param("now") LocalDateTime now);
    
    /**
     * Count available tickets for an event.
     * This is the single source of truth for available tickets count.
     * Should be cached in Redis to avoid repeated queries.
     * 
     * @param eventId Event ID
     * @return Number of tickets with status='AVAILABLE' for this event
     */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.event.id = :eventId AND t.status = 'AVAILABLE'")
    int countAvailableByEventId(@Param("eventId") Long eventId);
}
