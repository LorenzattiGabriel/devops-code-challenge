package com.ticketing.exception;

public class NoTicketsAvailableException extends RuntimeException {
    public NoTicketsAvailableException(String message) {
        super(message);
    }
    
    public NoTicketsAvailableException(Long eventId) {
        super(String.format("No tickets available for event with id: %d", eventId));
    }
}

