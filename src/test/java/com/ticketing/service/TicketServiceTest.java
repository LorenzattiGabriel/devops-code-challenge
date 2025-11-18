package com.ticketing.service;

import com.ticketing.exception.NoTicketsAvailableException;
import com.ticketing.exception.ResourceNotFoundException;
import com.ticketing.model.Event;
import com.ticketing.model.Ticket;
import com.ticketing.repository.EventRepository;
import com.ticketing.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TicketService.
 * Tests business logic with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {
    
    @Mock
    private TicketRepository ticketRepository;
    
    @Mock
    private EventRepository eventRepository;
    
    @Mock
    private DistributedLockService lockService;
    
    @InjectMocks
    private TicketService ticketService;
    
    private Event testEvent;
    private Ticket testTicket;
    
    @BeforeEach
    void setUp() {
        testEvent = new Event("Test Concert", "Test Venue", LocalDateTime.now().plusDays(1), 100);
        testEvent.setId(1L);
        
        testTicket = new Ticket(testEvent, "AVAILABLE");
        testTicket.setId(1L);
    }
    
    @Test
    @DisplayName("Should return available tickets for event")
    void getAvailableTickets_WhenEventExists_ShouldReturnTickets() {
        // Given
        List<Ticket> tickets = Arrays.asList(testTicket);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(testEvent));
        when(ticketRepository.findAvailableByEventId(1L)).thenReturn(tickets);
        
        // When
        List<Ticket> result = ticketService.getAvailableTickets(1L);
        
        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("AVAILABLE");
        
        verify(eventRepository, times(1)).findById(1L);
        verify(ticketRepository, times(1)).findAvailableByEventId(1L);
    }
    
    @Test
    @DisplayName("Should throw ResourceNotFoundException when event not found")
    void getAvailableTickets_WhenEventNotFound_ShouldThrowException() {
        // Given
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> ticketService.getAvailableTickets(999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Event")
            .hasMessageContaining("999");
        
        verify(eventRepository, times(1)).findById(999L);
        verify(ticketRepository, never()).findAvailableByEventId(anyLong());
    }
    
    @Test
    @DisplayName("Should reserve ticket successfully with distributed lock")
    void reserveTicket_WhenTicketsAvailable_ShouldReserveSuccessfully() {
        // Given
        when(eventRepository.findById(1L)).thenReturn(Optional.of(testEvent));
        
        // Mock distributed lock to execute the action immediately
        when(lockService.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any()))
            .thenAnswer(invocation -> {
                // Execute the lambda (5th parameter)
                return ((java.util.function.Supplier<?>) invocation.getArgument(4)).get();
            });
        
        when(ticketRepository.findFirstAvailableWithLock(1L)).thenReturn(Optional.of(testTicket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        Ticket result = ticketService.reserveTicket(1L, "customer@example.com");
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("RESERVED");
        assertThat(result.getCustomerEmail()).isEqualTo("customer@example.com");
        assertThat(result.getReservedUntil()).isAfter(LocalDateTime.now());
        
        verify(eventRepository, times(1)).findById(1L);
        verify(lockService, times(1)).executeWithLock(
            eq("ticket:reserve:event:1"),
            eq(3L),
            eq(10L),
            eq(TimeUnit.SECONDS),
            any()
        );
        verify(ticketRepository, times(1)).save(any(Ticket.class));
    }
    
    @Test
    @DisplayName("Should throw NoTicketsAvailableException when no tickets")
    void reserveTicket_WhenNoTicketsAvailable_ShouldThrowException() {
        // Given
        when(eventRepository.findById(1L)).thenReturn(Optional.of(testEvent));
        
        when(lockService.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any()))
            .thenAnswer(invocation -> {
                return ((java.util.function.Supplier<?>) invocation.getArgument(4)).get();
            });
        
        when(ticketRepository.findFirstAvailableWithLock(1L)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> ticketService.reserveTicket(1L, "customer@example.com"))
            .isInstanceOf(NoTicketsAvailableException.class)
            .hasMessageContaining("1");
        
        verify(ticketRepository, never()).save(any(Ticket.class));
    }
    
    @Test
    @DisplayName("Should throw ResourceNotFoundException when event not found for reservation")
    void reserveTicket_WhenEventNotFound_ShouldThrowException() {
        // Given
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> ticketService.reserveTicket(999L, "customer@example.com"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Event")
            .hasMessageContaining("999");
        
        verify(lockService, never()).executeWithLock(anyString(), anyLong(), anyLong(), any(), any());
        verify(ticketRepository, never()).save(any());
    }
    
    @Test
    @DisplayName("Should return customer tickets")
    void getCustomerTickets_ShouldReturnTickets() {
        // Given
        List<Ticket> tickets = Arrays.asList(testTicket);
        when(ticketRepository.findByCustomerEmail("customer@example.com")).thenReturn(tickets);
        
        // When
        List<Ticket> result = ticketService.getCustomerTickets("customer@example.com");
        
        // Then
        assertThat(result).hasSize(1);
        verify(ticketRepository, times(1)).findByCustomerEmail("customer@example.com");
    }
    
    @Test
    @DisplayName("Should cleanup expired reservations successfully")
    void cleanupExpiredReservations_ShouldReleaseExpiredTickets() {
        // Given
        Ticket expiredTicket1 = new Ticket(testEvent, "RESERVED");
        expiredTicket1.setId(1L);
        expiredTicket1.setCustomerEmail("customer1@example.com");
        expiredTicket1.setReservedUntil(LocalDateTime.now().minusMinutes(5));
        
        Ticket expiredTicket2 = new Ticket(testEvent, "RESERVED");
        expiredTicket2.setId(2L);
        expiredTicket2.setCustomerEmail("customer2@example.com");
        expiredTicket2.setReservedUntil(LocalDateTime.now().minusMinutes(10));
        
        List<Ticket> expiredTickets = Arrays.asList(expiredTicket1, expiredTicket2);
        
        when(ticketRepository.findExpiredReservations(any(LocalDateTime.class))).thenReturn(expiredTickets);
        when(ticketRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        ticketService.cleanupExpiredReservations();
        
        // Then
        assertThat(expiredTicket1.getStatus()).isEqualTo("AVAILABLE");
        assertThat(expiredTicket1.getCustomerEmail()).isNull();
        assertThat(expiredTicket1.getReservedUntil()).isNull();
        
        assertThat(expiredTicket2.getStatus()).isEqualTo("AVAILABLE");
        assertThat(expiredTicket2.getCustomerEmail()).isNull();
        assertThat(expiredTicket2.getReservedUntil()).isNull();
        
        verify(ticketRepository, times(1)).findExpiredReservations(any(LocalDateTime.class));
        verify(ticketRepository, times(1)).saveAll(expiredTickets);
    }
    
    @Test
    @DisplayName("Should do nothing when no expired reservations")
    void cleanupExpiredReservations_WhenNoExpired_ShouldDoNothing() {
        // Given
        when(ticketRepository.findExpiredReservations(any(LocalDateTime.class))).thenReturn(List.of());
        
        // When
        ticketService.cleanupExpiredReservations();
        
        // Then
        verify(ticketRepository, times(1)).findExpiredReservations(any(LocalDateTime.class));
        verify(ticketRepository, never()).saveAll(anyList());
    }
}

