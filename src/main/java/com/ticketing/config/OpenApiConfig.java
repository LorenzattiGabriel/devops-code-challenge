package com.ticketing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ticketingOpenAPI() {
        Server localServer = new Server();
        localServer.setUrl("http://localhost:8080");
        localServer.setDescription("Local Development Server");

        Server prodServer = new Server();
        prodServer.setUrl("https://api.ticketing.com");
        prodServer.setDescription("Production Server");

        Contact contact = new Contact();
        contact.setEmail("support@ticketing.com");
        contact.setName("Ticketing API Support");

        Info info = new Info()
                .title("Ticketing Service API")
                .version("1.0.0")
                .contact(contact)
                .description("API for event management and ticket reservation. " +
                        "Allows querying events, viewing available tickets, and making reservations.");

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer, prodServer));
    }
}

