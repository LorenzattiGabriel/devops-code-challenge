package com.ticketing.service;

import com.ticketing.exception.ResourceNotFoundException;
import com.ticketing.model.Event;
import com.ticketing.repository.EventRepository;
import com.ticketing.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {
    
    @Mock
    private EventRepository eventRepository;
    
    @Mock
    private TicketRepository ticketRepository;
    
    @InjectMocks
    private EventService eventService;
    
    private Event testEvent;
    
    @BeforeEach
    void setUp() {
        testEvent = new Event("Test Concert", "Test Venue", LocalDateTime.now().plusDays(1), 100);
        testEvent.setId(1L);
    }
    
    @Test
    @DisplayName("Should return event by ID when exists")
    void getEventById_WhenExists_ShouldReturnEvent() {
        // Given
        when(eventRepository.findById(1L)).thenReturn(Optional.of(testEvent));
        when(ticketRepository.countAvailableByEventId(1L)).thenReturn(50);
        
        // When
        Event result = eventService.getEventById(1L);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Test Concert");
        assertThat(result.getAvailableTickets()).isEqualTo(50);
        
        verify(eventRepository, times(1)).findById(1L);
        verify(ticketRepository, times(1)).countAvailableByEventId(1L);
    }
    
    @Test
    @DisplayName("Should throw ResourceNotFoundException when event not found")
    void getEventById_WhenNotExists_ShouldThrowException() {
        // Given
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> eventService.getEventById(999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Event")
            .hasMessageContaining("999");
        
        verify(eventRepository, times(1)).findById(999L);
        verify(ticketRepository, never()).countAvailableByEventId(anyLong());
    }
    
    @Test
    @DisplayName("Should return all events with available tickets count")
    void getAllEvents_ShouldReturnEventsWithAvailableTicketsCount() {
        // Given
        Event event1 = new Event("Event 1", "Venue 1", LocalDateTime.now().plusDays(1), 100);
        event1.setId(1L);
        
        Event event2 = new Event("Event 2", "Venue 2", LocalDateTime.now().plusDays(2), 200);
        event2.setId(2L);
        
        List<Event> events = Arrays.asList(event1, event2);
        
        when(eventRepository.findAll()).thenReturn(events);
        when(ticketRepository.countAvailableByEventId(1L)).thenReturn(50);
        when(ticketRepository.countAvailableByEventId(2L)).thenReturn(150);
        
        // When
        List<Event> result = eventService.getAllEvents();
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAvailableTickets()).isNotNull();
        assertThat(result.get(0).getAvailableTickets()).isEqualTo(50);
        assertThat(result.get(1).getAvailableTickets()).isEqualTo(150);
        
        verify(eventRepository, times(1)).findAll();
        verify(ticketRepository, times(1)).countAvailableByEventId(1L);
        verify(ticketRepository, times(1)).countAvailableByEventId(2L);
    }
    
    @Test
    @DisplayName("Should return paginated events")
    void getAllEventsPaged_ShouldReturnPagedEvents() {
        // Given
        PageRequest pageRequest = PageRequest.of(0, 20);
        Page<Event> eventsPage = new PageImpl<>(Arrays.asList(testEvent), pageRequest, 1);
        
        when(eventRepository.findAll(pageRequest)).thenReturn(eventsPage);
        when(ticketRepository.countAvailableByEventId(1L)).thenReturn(50);
        
        // When
        Page<Event> result = eventService.getAllEventsPaged(pageRequest);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAvailableTickets()).isEqualTo(50);
        assertThat(result.getTotalElements()).isEqualTo(1);
        
        verify(eventRepository, times(1)).findAll(pageRequest);
        verify(ticketRepository, times(1)).countAvailableByEventId(1L);
    }
    
    @Test
    @DisplayName("Should create event successfully")
    void createEvent_ShouldSaveAndReturnEvent() {
        // Given
        Event newEvent = new Event("New Concert", "New Venue", LocalDateTime.now().plusDays(5), 300);
        Event savedEvent = new Event("New Concert", "New Venue", LocalDateTime.now().plusDays(5), 300);
        savedEvent.setId(2L);
        
        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);
        
        // When
        Event result = eventService.createEvent(newEvent);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getName()).isEqualTo("New Concert");
        
        verify(eventRepository, times(1)).save(newEvent);
    }
    
    @Test
    @DisplayName("Should return events with available tickets")
    void getEventsWithAvailableTickets_ShouldReturnFilteredEvents() {
        // Given
        List<Event> eventsWithTickets = Arrays.asList(testEvent);
        
        when(eventRepository.findEventsWithAvailableTickets()).thenReturn(eventsWithTickets);
        when(ticketRepository.countAvailableByEventId(1L)).thenReturn(50);
        
        // When
        List<Event> result = eventService.getEventsWithAvailableTickets();
        
        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAvailableTickets()).isEqualTo(50);
        
        verify(eventRepository, times(1)).findEventsWithAvailableTickets();
        verify(ticketRepository, times(1)).countAvailableByEventId(1L);
    }
    
    @Test
    @DisplayName("Should get available tickets count from cache")
    void getAvailableTicketsCount_ShouldQueryRepository() {
        // Given
        when(ticketRepository.countAvailableByEventId(1L)).thenReturn(75);
        
        // When
        int count = eventService.getAvailableTicketsCount(1L);
        
        // Then
        assertThat(count).isEqualTo(75);
        verify(ticketRepository, times(1)).countAvailableByEventId(1L);
    }
}

