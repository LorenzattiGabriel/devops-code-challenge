# 🚀 Local Development Setup

Complete guide to run the ticketing microservice locally using Docker Compose.

---

## 📋 Prerequisites

- **Docker** (v20+) and **Docker Compose** (v2+)
- **Java 17+** (for local development without Docker)
- **Maven 3.9+** (for building the project)
- **MySQL Client** (for database verification - optional)

### Install MySQL Client (macOS)
```bash
brew install mysql-client
echo 'export PATH="/opt/homebrew/opt/mysql-client/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

---

## 🏗️ Architecture

The local environment mirrors production and includes:

- **Spring Boot Application** (port 8080) - Ticketing REST API
- **MySQL Database** (port 3306) - Persistent data storage
- **Redis** (port 6379) - Caching and distributed locking
- **Nginx** (port 80) - Load balancer with intelligent caching

### Nginx Caching Strategy
- `GET /api/v1/events` → **5 minutes** cache
- `GET /api/v1/events/{id}` → **2 minutes** cache
- `GET /api/v1/events/available` → **2 minutes** cache
- `POST /api/v1/tickets/*` → **NO CACHE** (mutations)

---

## 🎯 Quick Start (Automated)

### Option 1: Using the setup script (Recommended)

```bash
# Grant execution permissions
chmod +x setup.sh

# Run the setup script
./setup.sh
```

The script will:
1. ✅ Verify Docker and Docker Compose are running
2. ✅ Build the application JAR with Maven
3. ✅ Start all services with Docker Compose
4. ✅ Wait for MySQL and Redis to be healthy
5. ✅ Initialize the database with sample data
6. ✅ Verify the application health
7. ✅ Display useful information and endpoints

### Option 2: Manual setup

```bash
# 1. Build the application
mvn clean package -DskipTests

# 2. Start all services
docker-compose up -d

# 3. Wait for services to be ready (30-60 seconds)
docker-compose ps

# 4. Initialize the database
docker exec -i ticketing-mysql mysql -uroot -prootpassword ticketing < src/main/resources/init-db.sql

# 5. Check application health
curl http://localhost:8080/actuator/health
```

---

## 📡 Available Endpoints

### API Base URL
- **Direct (Development):** `http://localhost:8080`
- **Via Nginx (Production-like):** `http://localhost`

### Swagger UI (API Documentation)
- **URL:** http://localhost:8080/swagger-ui/index.html

### Health Check
- **URL:** http://localhost:8080/actuator/health

### Event Endpoints
```bash
# Get all events
curl http://localhost/api/v1/events

# Get event by ID
curl http://localhost/api/v1/events/1

# Get events with available tickets
curl http://localhost/api/v1/events/available

# Get paginated events
curl "http://localhost/api/v1/events/paged?page=0&size=10&sort=eventDate,desc"

# Create new event
curl -X POST http://localhost:8080/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Rock Concert 2026",
    "venue": "Madison Square Garden",
    "eventDate": "2026-12-31T20:00:00",
    "totalTickets": 500
  }'
```

### Ticket Endpoints
```bash
# Get available tickets for an event
curl http://localhost/api/v1/tickets/event/1

# Reserve a ticket
curl -X POST "http://localhost:8080/api/v1/tickets/reserve?eventId=1&customerEmail=john@example.com"

# Get customer tickets
curl http://localhost/api/v1/tickets/customer/john@example.com
```

---

## 🐳 Docker Commands

### View logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f app
docker-compose logs -f mysql
docker-compose logs -f redis
docker-compose logs -f nginx
```

### Restart services
```bash
# All services
docker-compose restart

# Specific service
docker-compose restart app
```

### Stop services
```bash
docker-compose down
```

### Clean everything (including volumes)
```bash
docker-compose down -v
```

### Rebuild application
```bash
# Rebuild JAR and restart
mvn clean package -DskipTests
docker-compose up -d --build app
```

---

## 🗄️ Database Access

### Via Docker
```bash
docker exec -it ticketing-mysql mysql -uroot -prootpassword ticketing
```

### Via MySQL Client
```bash
mysql -h 127.0.0.1 -P 3306 -u root -prootpassword ticketing
```

### Useful SQL Queries
```sql
-- View all events
SELECT id, name, venue, event_date, total_tickets FROM events;

-- View tickets with status
SELECT t.id, e.name AS event, t.status, t.customer_email, t.reserved_until 
FROM tickets t 
JOIN events e ON t.event_id = e.id 
LIMIT 20;

-- Count available tickets per event
SELECT e.name, 
       COUNT(CASE WHEN t.status = 'AVAILABLE' THEN 1 END) as available,
       COUNT(*) as total
FROM events e 
LEFT JOIN tickets t ON e.id = t.event_id 
GROUP BY e.id, e.name;
```

---

## 🔧 Running Application Locally (IntelliJ/IDE)

For hot-reload development, run the Spring Boot app locally while using Dockerized MySQL and Redis:

### 1. Start only MySQL and Redis
```bash
docker-compose up -d mysql redis
```

### 2. Set Active Profile
```bash
export SPRING_PROFILES_ACTIVE=dev
```

### 3. Run from IDE
- Open `TicketingApplication.java`
- Click Run/Debug
- Application will connect to Docker MySQL and Redis
- Changes are applied with hot-reload

### 4. Access the application
- **Direct:** http://localhost:8080/api/v1/events
- **Swagger:** http://localhost:8080/swagger-ui/index.html

---

## 🧪 Testing

### Run all tests
```bash
mvn test
```

### Run specific test class
```bash
mvn test -Dtest=TicketServiceTest
```

### View code coverage
```bash
mvn test
open target/site/jacoco/index.html
```

---

## 🐛 Troubleshooting

### Issue: "Port 8080 already in use"
**Solution:**
```bash
# Find and kill the process
lsof -ti:8080 | xargs kill -9

# Or change the application port
export SERVER_PORT=8081
```

### Issue: "Cannot connect to MySQL"
**Solution:**
```bash
# Check MySQL is healthy
docker-compose ps mysql

# View MySQL logs
docker-compose logs mysql

# Restart MySQL
docker-compose restart mysql

# Wait for MySQL to be ready (can take 30-60 seconds)
docker exec ticketing-mysql mysqladmin ping -h localhost -uroot -prootpassword
```

### Issue: "Cannot connect to Redis"
**Solution:**
```bash
# Check Redis is running
docker-compose ps redis

# Test Redis connection
docker exec ticketing-redis redis-cli ping

# Should return: PONG
```

### Issue: "Nginx 502 Bad Gateway"
**Solution:**
```bash
# Check app is running
curl http://localhost:8080/actuator/health

# Restart Nginx
docker-compose restart nginx
```

### Issue: "Tests failing"
**Solution:**
```bash
# Clean and rebuild
mvn clean install

# If embedded Redis issues
mvn clean test -DskipTests=false
```

### Issue: "Docker daemon not running"
**Solution:**
- Open Docker Desktop
- Wait for Docker to fully start
- Check status: `docker ps`

---

## 🏷️ Environment Variables

### Development (application-dev.properties)
- `SPRING_PROFILES_ACTIVE=dev`
- Database: H2 in-memory (for tests) or MySQL (Docker)
- Redis: localhost:6379

### Production (application-prod.properties)
- `SPRING_PROFILES_ACTIVE=prod`
- Database: MySQL with connection pooling
- Redis: Clustered Redis
- Metrics: Actuator endpoints restricted

---

## 📊 Monitoring

### Application Health
```bash
curl http://localhost:8080/actuator/health
```

### Application Info
```bash
curl http://localhost:8080/actuator/info
```

### Redis Cache Statistics
```bash
docker exec ticketing-redis redis-cli INFO stats
```

### MySQL Performance
```bash
docker exec -it ticketing-mysql mysql -uroot -prootpassword -e "SHOW PROCESSLIST"
```

---

## 🔐 Security

### CORS Configuration
- **Enabled** via Spring Security
- **Allowed Origins:** localhost:3000, localhost:4200, localhost:8080
- **Allowed Methods:** GET, POST, PUT, PATCH, DELETE, OPTIONS
- **No Authentication Required** (public API)

### Production Recommendations
- Enable authentication (JWT/OAuth2)
- Restrict CORS origins to specific domains
- Enable HTTPS
- Configure rate limiting
- Use API Gateway

---

## 📦 Sample Data

The database is initialized with sample events and tickets:

**Events:**
- Spring Festival 2026 - 1000 tickets
- Summer Music Fest 2026 - 500 tickets  
- Tech Conference 2026 - 300 tickets

All events are set in the future with various available tickets for testing.

---

## 🚀 Next Steps

1. **Explore the API** - Use Swagger UI to test endpoints
2. **Run Tests** - `mvn test` to verify everything works
3. **Make Changes** - Edit code and test with hot-reload
4. **Deploy to K8s** - See `k8s/README.md` for deployment guide

---

## 📚 Additional Resources

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Redis Documentation](https://redis.io/documentation)
- [Nginx Documentation](https://nginx.org/en/docs/)

---

## 💡 Tips

- Use **Docker Desktop** for easy container management
- Use **Postman** or **Insomnia** to save API requests
- Check **logs** first when debugging issues
- **Restart containers** to apply configuration changes
- Use **Redis CLI** to inspect cached data: `docker exec -it ticketing-redis redis-cli`

---

**Happy Coding! 🎉**
