#!/bin/bash

###############################################################################
# Ticketing Service - Local Development Setup Script
# 
# This script automates the complete setup process:
# 1. Starts Docker Compose services (MySQL, Redis, Nginx, App)
# 2. Waits for services to be healthy
# 3. Initializes the database with sample data
# 4. Verifies the application is ready
#
# Usage: ./setup.sh [options]
#   --skip-build    Skip Docker image build
#   --reset-db      Drop and recreate database
#   --help          Show this help message
###############################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
COMPOSE_FILE="docker-compose.yml"
MAX_WAIT=120  # Maximum wait time in seconds
DB_HOST="localhost"
DB_PORT="3306"
DB_NAME="ticketing"
DB_USER="ticketing_user"
DB_PASS="ticketing_pass"
REDIS_PORT="6379"
APP_PORT="8080"
NGINX_PORT="80"

# Parse arguments
SKIP_BUILD=false
RESET_DB=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --reset-db)
            RESET_DB=true
            shift
            ;;
        --help)
            head -n 14 "$0" | tail -n 11
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

###############################################################################
# Helper Functions
###############################################################################

print_header() {
    echo -e "\n${BLUE}===================================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}===================================================${NC}\n"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

check_command() {
    if ! command -v $1 &> /dev/null; then
        print_error "$1 is not installed"
        echo -e "Please install $1 and try again"
        exit 1
    fi
}

wait_for_service() {
    local service=$1
    local host=$2
    local port=$3
    local max_attempts=$((MAX_WAIT / 2))
    local attempt=1

    print_info "Waiting for $service to be ready..."
    
    while [ $attempt -le $max_attempts ]; do
        if nc -z $host $port 2>/dev/null; then
            print_success "$service is ready"
            return 0
        fi
        
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    print_error "$service failed to start within ${MAX_WAIT}s"
    return 1
}

wait_for_mysql() {
    local max_attempts=$((MAX_WAIT / 5))
    local attempt=1

    print_info "Waiting for MySQL to accept connections (this may take up to 2 minutes on first run)..."
    
    while [ $attempt -le $max_attempts ]; do
        if docker exec ticketing-mysql mysqladmin ping -h localhost -u root -prootpass --silent &> /dev/null; then
            print_success "MySQL is ready"
            return 0
        fi
        
        echo -n "."
        sleep 5
        attempt=$((attempt + 1))
    done
    
    print_error "MySQL failed to start within ${MAX_WAIT}s"
    return 1
}

wait_for_app_health() {
    local max_attempts=$((MAX_WAIT / 2))
    local attempt=1

    print_info "Waiting for application health check..."
    
    while [ $attempt -le $max_attempts ]; do
        response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:${APP_PORT}/actuator/health 2>/dev/null || echo "000")
        
        if [ "$response" = "200" ]; then
            print_success "Application is healthy"
            return 0
        fi
        
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    print_error "Application health check failed within ${MAX_WAIT}s"
    return 1
}

###############################################################################
# Main Setup Process
###############################################################################

print_header "Ticketing Service - Local Development Setup"

# Step 1: Check prerequisites
print_info "Checking prerequisites..."
check_command docker
check_command docker-compose
check_command nc
check_command curl
check_command mysql
print_success "All prerequisites are installed"

# Step 2: Stop existing containers
print_info "Stopping existing containers..."
docker-compose down -v 2>/dev/null || true
print_success "Existing containers stopped"

# Step 3: Build images (if not skipped)
if [ "$SKIP_BUILD" = false ]; then
    print_header "Building Docker Images"
    docker-compose build --no-cache
    print_success "Docker images built successfully"
else
    print_warning "Skipping Docker image build"
fi

# Step 4: Start services
print_header "Starting Services"
docker-compose up -d
print_success "Docker Compose services started"

# Step 5: Wait for MySQL
print_header "Waiting for MySQL"
if ! wait_for_service "MySQL" "$DB_HOST" "$DB_PORT"; then
    print_error "Setup failed: MySQL not ready"
    docker-compose logs mysql
    exit 1
fi

if ! wait_for_mysql; then
    print_error "Setup failed: MySQL not accepting connections"
    docker-compose logs mysql
    exit 1
fi

# Step 6: Wait for Redis
print_header "Waiting for Redis"
if ! wait_for_service "Redis" "$DB_HOST" "$REDIS_PORT"; then
    print_error "Setup failed: Redis not ready"
    docker-compose logs redis
    exit 1
fi

# Test Redis connection
if docker exec ticketing-redis redis-cli ping | grep -q PONG; then
    print_success "Redis is responding to commands"
else
    print_error "Redis is not responding"
    exit 1
fi

# Step 7: Initialize Database
print_header "Initializing Database"

# Check if database already has data
existing_data=$(mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -p$DB_PASS $DB_NAME -e "SELECT COUNT(*) as count FROM events;" 2>/dev/null | tail -1 || echo "0")

if [ "$existing_data" != "0" ] && [ "$RESET_DB" = false ]; then
    print_warning "Database already contains data (${existing_data} events)"
    print_info "Use --reset-db flag to reinitialize"
else
    print_info "Executing database initialization script..."
    
    if mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -p$DB_PASS $DB_NAME < src/main/resources/init-db.sql; then
        print_success "Database initialized successfully"
        
        # Verify data
        event_count=$(mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -p$DB_PASS $DB_NAME -e "SELECT COUNT(*) FROM events;" 2>/dev/null | tail -1)
        ticket_count=$(mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -p$DB_PASS $DB_NAME -e "SELECT COUNT(*) FROM tickets;" 2>/dev/null | tail -1)
        
        print_success "Created $event_count events and $ticket_count tickets"
    else
        print_error "Database initialization failed"
        exit 1
    fi
fi

# Step 8: Wait for Application
print_header "Waiting for Application"
if ! wait_for_service "Application" "$DB_HOST" "$APP_PORT"; then
    print_error "Setup failed: Application not ready"
    docker-compose logs app
    exit 1
fi

if ! wait_for_app_health; then
    print_error "Setup failed: Application health check failed"
    docker-compose logs app
    exit 1
fi

# Step 9: Wait for Nginx
print_header "Waiting for Nginx"
if ! wait_for_service "Nginx" "$DB_HOST" "$NGINX_PORT"; then
    print_error "Setup failed: Nginx not ready"
    docker-compose logs nginx
    exit 1
fi

# Step 10: Verify Setup
print_header "Verifying Setup"

# Test API through Nginx
print_info "Testing API endpoints..."

# Test health endpoint
if curl -s http://localhost/actuator/health | grep -q "UP"; then
    print_success "Health endpoint is working"
else
    print_error "Health endpoint is not responding correctly"
fi

# Test events endpoint
event_response=$(curl -s http://localhost/api/v1/events)
if echo "$event_response" | grep -q "Spring"; then
    print_success "Events API is working"
else
    print_error "Events API is not responding correctly"
fi

# Check Redis cache
print_info "Checking Redis cache..."
cache_keys=$(docker exec ticketing-redis redis-cli KEYS '*' 2>/dev/null | wc -l)
print_success "Redis has $cache_keys cache keys"

###############################################################################
# Setup Complete
###############################################################################

print_header "Setup Complete! 🎉"

echo -e "${GREEN}All services are running and healthy!${NC}\n"

echo -e "${BLUE}Service URLs:${NC}"
echo -e "  • Application:    http://localhost:${APP_PORT}"
echo -e "  • Nginx (LB):     http://localhost:${NGINX_PORT}"
echo -e "  • Swagger UI:     http://localhost:${APP_PORT}/swagger-ui/index.html"
echo -e "  • API Docs:       http://localhost:${APP_PORT}/v3/api-docs"
echo -e "  • Health Check:   http://localhost/actuator/health\n"

echo -e "${BLUE}Database Connection:${NC}"
echo -e "  • Host:           ${DB_HOST}"
echo -e "  • Port:           ${DB_PORT}"
echo -e "  • Database:       ${DB_NAME}"
echo -e "  • Username:       ${DB_USER}"
echo -e "  • Password:       ${DB_PASS}\n"

echo -e "${BLUE}Quick Test Commands:${NC}"
echo -e "  • List events:    curl http://localhost/api/v1/events"
echo -e "  • Get event:      curl http://localhost/api/v1/events/1"
echo -e "  • Available:      curl http://localhost/api/v1/events/available"
echo -e "  • Reserve ticket: curl -X POST 'http://localhost:${APP_PORT}/api/v1/tickets/reserve?eventId=1&customerEmail=test@example.com'\n"

echo -e "${BLUE}Useful Commands:${NC}"
echo -e "  • View logs:      docker-compose logs -f [service]"
echo -e "  • Restart:        docker-compose restart [service]"
echo -e "  • Stop all:       docker-compose down"
echo -e "  • Clean all:      docker-compose down -v\n"

echo -e "${YELLOW}Note:${NC} For IntelliJ development, use profile 'dev' and connect to these services.\n"

