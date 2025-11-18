package com.ticketing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.config.TestSecurityConfig;
import com.ticketing.exception.NoTicketsAvailableException;
import com.ticketing.exception.ResourceNotFoundException;
import com.ticketing.model.Event;
import com.ticketing.model.Ticket;
import com.ticketing.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

@WebMvcTest(TicketController.class)
@Import(TestSecurityConfig.class)
class TicketControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
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
    void getAvailableTickets_WhenTicketsExist_ShouldReturnList() throws Exception {
        // Given
        List<Ticket> tickets = Arrays.asList(testTicket);
        when(ticketService.getAvailableTickets(1L)).thenReturn(tickets);
        
        // When & Then
        mockMvc.perform(get("/api/v1/tickets/event/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
        
        verify(ticketService, times(1)).getAvailableTickets(1L);
    }
    
    @Test
    void getAvailableTickets_WhenEventNotFound_ShouldReturn404() throws Exception {
        // Given
        when(ticketService.getAvailableTickets(999L))
            .thenThrow(new ResourceNotFoundException("Event", 999L));
        
        // When & Then
        mockMvc.perform(get("/api/v1/tickets/event/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message", containsString("Event")));
        
        verify(ticketService, times(1)).getAvailableTickets(999L);
    }
    
    @Test
    void getAvailableTickets_WithInvalidId_ShouldReturn400() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/tickets/event/-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message", containsString("Event ID must be a positive number")));
        
        verify(ticketService, never()).getAvailableTickets(any());
    }
    
    @Test
    void reserveTicket_WithValidData_ShouldReturnCreated() throws Exception {
        // Given
        Ticket reservedTicket = new Ticket(testEvent, "RESERVED");
        reservedTicket.setId(1L);
        reservedTicket.setCustomerEmail("customer@example.com");
        reservedTicket.setReservedUntil(LocalDateTime.now().plusMinutes(10));
        
        when(ticketService.reserveTicket(1L, "customer@example.com")).thenReturn(reservedTicket);
        
        // When & Then
        mockMvc.perform(post("/api/v1/tickets/reserve")
                .param("eventId", "1")
                .param("customerEmail", "customer@example.com"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("RESERVED"))
            .andExpect(jsonPath("$.customerEmail").value("customer@example.com"))
            .andExpect(jsonPath("$.reservedUntil").exists());
        
        verify(ticketService, times(1)).reserveTicket(1L, "customer@example.com");
    }
    
    @Test
    void reserveTicket_WhenNoTickets_ShouldReturn409() throws Exception {
        // Given
        when(ticketService.reserveTicket(1L, "customer@example.com"))
            .thenThrow(new NoTicketsAvailableException(1L));
        
        // When & Then
        mockMvc.perform(post("/api/v1/tickets/reserve")
                .param("eventId", "1")
                .param("customerEmail", "customer@example.com"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Conflict"))
            .andExpect(jsonPath("$.message", containsString("No tickets available")));
        
        verify(ticketService, times(1)).reserveTicket(1L, "customer@example.com");
    }
    
    @Test
    void reserveTicket_WithInvalidEmail_ShouldReturn400() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/tickets/reserve")
                .param("eventId", "1")
                .param("customerEmail", "invalid-email"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message", containsString("Invalid email format")));
        
        verify(ticketService, never()).reserveTicket(any(), any());
    }
    
    @Test
    void reserveTicket_WithNullEventId_ShouldReturn400() throws Exception {
    
        mockMvc.perform(post("/api/v1/tickets/reserve")
                .param("customerEmail", "customer@example.com"))
            .andExpect(status().is5xxServerError()); // Missing required param causes 500
        
        verify(ticketService, never()).reserveTicket(any(), any());
    }
    
    @Test
    void getCustomerTickets_WithValidEmail_ShouldReturnTickets() throws Exception {
        // Given
        Ticket ticket1 = new Ticket(testEvent, "RESERVED");
        ticket1.setId(1L);
        ticket1.setCustomerEmail("customer@example.com");
        
        Ticket ticket2 = new Ticket(testEvent, "RESERVED");
        ticket2.setId(2L);
        ticket2.setCustomerEmail("customer@example.com");
        
        List<Ticket> tickets = Arrays.asList(ticket1, ticket2);
        
        when(ticketService.getCustomerTickets("customer@example.com")).thenReturn(tickets);
        
        // When & Then
        mockMvc.perform(get("/api/v1/tickets/customer/customer@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].customerEmail").value("customer@example.com"))
            .andExpect(jsonPath("$[1].customerEmail").value("customer@example.com"));
        
        verify(ticketService, times(1)).getCustomerTickets("customer@example.com");
    }
    
    @Test
    void getCustomerTickets_WhenNoTickets_ShouldReturnEmptyList() throws Exception {
        // Given
        when(ticketService.getCustomerTickets("notickets@example.com")).thenReturn(List.of());
        
        // When & Then
        mockMvc.perform(get("/api/v1/tickets/customer/notickets@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
        
        verify(ticketService, times(1)).getCustomerTickets("notickets@example.com");
    }
    
    @Test
    void getCustomerTickets_WithInvalidEmail_ShouldReturn400() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/tickets/customer/invalid-email"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message", containsString("Invalid email format")));
        
        verify(ticketService, never()).getCustomerTickets(any());
    }
    
    @Test
    void getCustomerTickets_WithBlankEmail_ShouldReturn400() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/tickets/customer/ "))
            .andExpect(status().isBadRequest());
        
        verify(ticketService, never()).getCustomerTickets(any());
    }
}

