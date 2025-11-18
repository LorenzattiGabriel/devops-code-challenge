# DevOps Code Challenge - Ticketing Service

## Overview
This is a Spring Boot application for a ticketing service that has several issues that need to be fixed. The application is currently deployed in AWS using Elastic Beanstalk with a load balancer, but our developers are struggling to run it locally and replicate production scenarios. Your task is to fix the application and create a local development environment that mirrors production.

## Phase 1: Fix the Application
The application has several core issues that prevent it from running properly:

### Issues to Fix:
1. **Application won't start** - Currently setup to run in a prod environment
2. **Basic functionality broken** - Endpoints return errors
3. **Race conditions** - The same ticket can be reserved multiple times

### Getting Started:
```bash
# Make sure you have Java 17+ and Maven installed
mvn clean install
mvn spring-boot:run
```

### Test the API:
```bash
# Get all events
curl http://localhost:8080/api/events

# Get event by ID
curl http://localhost:8080/api/events/1

# Get available tickets for an event
curl http://localhost:8080/api/tickets/event/1

# Reserve a ticket
curl -X POST "http://localhost:8080/api/tickets/reserve?eventId=1&customerEmail=test@example.com"

Running the below command should not allow the same ticket to be purchased twice

echo "=== RACE CONDITION DEMONSTRATION ===" && echo "Making 5 concurrent requests for 3 available tickets..." && echo "" && (echo "Request 1:" && curl -s -X POST "http://localhost:8080/api/tickets/reserve?eventId=2&customerEmail=race1@example.com" | jq '.id // "FAILED"') & (echo "Request 2:" && curl -s -X POST "http://localhost:8080/api/tickets/reserve?eventId=2&customerEmail=race2@example.com" | jq '.id // "FAILED"') & (echo "Request 3:" && curl -s -X POST "http://localhost:8080/api/tickets/reserve?eventId=2&customerEmail=race3@example.com" | jq '.id // "FAILED"') & (echo "Request 4:" && curl -s -X POST "http://localhost:8080/api/tickets/reserve?eventId=2&customerEmail=race4@example.com" | jq '.id // "FAILED"') & (echo "Request 5:" && curl -s -X POST "http://localhost:8080/api/tickets/reserve?eventId=2&customerEmail=race5@example.com" | jq '.id // "FAILED"') & wait
```

## Phase 2: Local Development Environment
Once the application is working, create a local development environment that mirrors production:

### Requirements:
1. **Containerize the application** using docker. Minimize image size
2. **Create Docker Compose setup** that mirrors production architecture:
   - Application container (Spring Boot app)
   - Load balancer (nginx) to simulate production ALB, add nginx caching for the endpoints defined below
   - Redis
3. **Create Kubernetes manifests** for future migration from Elastic Beanstalk
5. **Document the setup** so developers can easily run locally with troubleshooting guide

### Expected Deliverables:
- Dockerfile
- docker-compose.yml with full local environment
- nginx configuration with specific caching rules:
  - Cache `/api/events` for 5 minutes
  - Cache `/api/events/{id}` for 2 minutes
  - Do NOT cache `/api/tickets/*` endpoints
- Kubernetes manifests (for future migration)
- Local development documentation with troubleshooting guide

## Phase 3: Production Discussion
Be prepared to discuss the code base and general systems design.

Good luck!
