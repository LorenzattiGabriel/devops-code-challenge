package com.ticketing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.config.TestSecurityConfig;
import com.ticketing.exception.ResourceNotFoundException;
import com.ticketing.model.Event;
import com.ticketing.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
@Import(TestSecurityConfig.class)
class EventControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private EventService eventService;
    
    private Event testEvent;
    
    @BeforeEach
    void setUp() {
        testEvent = new Event("Test Concert", "Test Venue", LocalDateTime.of(2025, 12, 31, 20, 0), 100);
        testEvent.setId(1L);
        testEvent.setAvailableTickets(50);
    }
    
    @Test
    void getAllEvents_ShouldReturnListOfEvents() throws Exception {
        // Given
        List<Event> events = Arrays.asList(testEvent);
        when(eventService.getAllEvents()).thenReturn(events);
        
        // When & Then
        mockMvc.perform(get("/api/v1/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].name").value("Test Concert"))
            .andExpect(jsonPath("$[0].venue").value("Test Venue"))
            .andExpect(jsonPath("$[0].availableTickets").value(50));
        
        verify(eventService, times(1)).getAllEvents();
    }
    
    @Test
    void getEvent_WhenExists_ShouldReturnEvent() throws Exception {
        // Given
        when(eventService.getEventById(1L)).thenReturn(testEvent);
        
        // When & Then
        mockMvc.perform(get("/api/v1/events/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("Test Concert"))
            .andExpect(jsonPath("$.availableTickets").value(50));
        
        verify(eventService, times(1)).getEventById(1L);
    }
    
    @Test
    void getEvent_WhenNotExists_ShouldReturn404() throws Exception {
        // Given
        when(eventService.getEventById(999L))
            .thenThrow(new ResourceNotFoundException("Event", 999L));
        
        // When & Then
        mockMvc.perform(get("/api/v1/events/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message", containsString("Event")));
        
        verify(eventService, times(1)).getEventById(999L);
    }
    
    @Test
    void getEvent_WithInvalidId_ShouldReturn400() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/events/-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"));
        
        verify(eventService, never()).getEventById(any());
    }
    
    @Test
    void createEvent_WithValidData_ShouldReturnCreated() throws Exception {
        // Given
        Event request = new Event("New Concert", "New Venue", LocalDateTime.of(2026, 1, 15, 20, 0), 500);
        
        Event createdEvent = new Event("New Concert", "New Venue", request.getEventDate(), 500);
        createdEvent.setId(2L);
        
        when(eventService.createEvent(any(Event.class))).thenReturn(createdEvent);
        
        // When & Then
        mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(2))
            .andExpect(jsonPath("$.name").value("New Concert"))
            .andExpect(jsonPath("$.venue").value("New Venue"));
        
        verify(eventService, times(1)).createEvent(any(Event.class));
    }
    
    @Test
    void createEvent_WithInvalidData_ShouldReturn400() throws Exception {
        // Given - Empty name (violates @NotBlank and @Size validations)
        Event request = new Event("", "Venue", LocalDateTime.of(2026, 1, 15, 20, 0), 100);
        
        // When & Then - Bean Validation should reject this before reaching the service
        mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("Event name")));
        
        // Service should NOT be called because validation fails at controller level
        verify(eventService, never()).createEvent(any());
    }
    
    @Test
    void createEvent_WithPastDate_ShouldReturn400() throws Exception {
        // Given - Past date (violates @Future validation)
        Event request = new Event("Concert", "Venue", LocalDateTime.of(2020, 1, 1, 20, 0), 100);

        // When & Then - Bean Validation should reject this before reaching the service
        mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("Event date must be in the future")));
        
        // Service should NOT be called because validation fails at controller level
        verify(eventService, never()).createEvent(any());
    }
    
    @Test
    void getEventsWithAvailableTickets_ShouldReturnFilteredEvents() throws Exception {
        // Given
        List<Event> events = Arrays.asList(testEvent);
        when(eventService.getEventsWithAvailableTickets()).thenReturn(events);
        
        // When & Then
        mockMvc.perform(get("/api/v1/events/available"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].availableTickets").value(50));
        
        verify(eventService, times(1)).getEventsWithAvailableTickets();
    }
}

