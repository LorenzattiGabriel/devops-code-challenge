# Technical Improvements Roadmap

###  Database Migrations (Liquibase)

**Current State:**
```properties
spring.jpa.hibernate.ddl-auto=update  # ⚠️ DANGEROUS in production
```

**Problem:**
- No version control for database schema
- Risk of data loss in production

**Recommended Solution:**
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>

**Benefits:**
- ✅ Version-controlled schema changes
- ✅ Audit trail of all database changes

---

### Authentication & Authorization

**Current State:**
```java
// SecurityConfig.java
.authorizeHttpRequests(auth -> auth
    .anyRequest().permitAll()  //  All endpoints are public
)
```

**Problem:**
- No user authentication
- Anyone can reserve tickets
- No audit trail of who did what

**Recommended Solution: JWT with Spring Security**
1. Add Spring Security OAuth2 Resource Server
2. 
**Benefits:**
- ✅ Secure user data
- ✅ Prevent abuse


### Scheduled Tasks Thread Pool

**Current State:**
```java
@Scheduled(fixedDelay = 300000)
public void cleanupExpiredReservations() {
    // Runs on single default scheduler thread
}
```

**Problem:**
- Spring uses **single thread** for all `@Scheduled` tasks by default
- Blocks other scheduled tasks from running
- No isolation between different task types

**Recommended Solution:**

```properties
# application.properties
spring.task.scheduling.pool.size=5
spring.task.scheduling.thread-name-prefix=Scheduled-
```

Or ThreadPoolTaskScheduler

**Benefits:**
- ✅ Multiple scheduled tasks run concurrently
- ✅ No blocking between tasks


###  HikariCP Connection Pool Tuning
use example 

spring.datasource.hikari.maximum-pool-size=20

**Current State:**
```properties
# Using HikariCP defaults:
# maximum-pool-size: 10
# minimum-idle: 10
# connection-timeout: 30000ms
```

**Problem:**
- Default pool size (10) may be insufficient under load
- Connection timeout not optimized


# Performance
spring.datasource.hikari.pool-name=TicketingHikariPool
```

**Pool Sizing :**
```
connections = ((core_count * 2) + effective_spindle_count)

**Benefits:**
- ✅ Better throughput under load
- ✅ Faster query response times

---


### Thread Pool Monitoring

**Current State:**
- No visibility into thread pool health
- Can't identify bottlenecks

** Solution:**
ThreadPoolTaskExecutor

**Benefits:**
- ✅ Identify thread pool saturation
- ✅ better performance

