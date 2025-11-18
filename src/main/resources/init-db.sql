-- Database initialization script for MySQL
-- This script creates tables and loads initial data
-- Note: availableTickets is NO LONGER stored in events table (calculated from tickets)

-- Drop tables if they exist (for clean setup)
DROP TABLE IF EXISTS tickets;
DROP TABLE IF EXISTS events;

-- Create events table
CREATE TABLE IF NOT EXISTS events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    venue VARCHAR(255) NOT NULL,
    event_date DATETIME(6) NOT NULL,
    total_tickets INT NOT NULL,
    INDEX idx_event_date (event_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Create tickets table
CREATE TABLE IF NOT EXISTS tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    customer_email VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    reserved_until DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    INDEX idx_event_status (event_id, status),
    INDEX idx_customer_email (customer_email),
    INDEX idx_status_event (status, event_id),
    INDEX idx_reserved_until (reserved_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Insert sample events
INSERT INTO events (name, venue, event_date, total_tickets) VALUES
('Spring Concert 2025', 'Madison Square Garden', '2025-12-15 19:00:00', 1000),
('Tech Conference 2025', 'Convention Center', '2025-12-20 09:00:00', 500),
('Jazz Festival 2025', 'Central Park', '2025-12-25 16:00:00', 2000),
('Comedy Night', 'Comedy Club', '2025-12-30 20:00:00', 200);

-- Generate tickets for Event 1 (Spring Concert - 1000 tickets)
INSERT INTO tickets (event_id, status, created_at)
SELECT 1, 'AVAILABLE', CURRENT_TIMESTAMP(6)
FROM (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
     (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
     (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t3
LIMIT 1000;

-- Generate tickets for Event 2 (Tech Conference - 500 tickets)
INSERT INTO tickets (event_id, status, created_at)
SELECT 2, 'AVAILABLE', CURRENT_TIMESTAMP(6)
FROM (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
     (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
     (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) t3
LIMIT 500;

-- Generate tickets for Event 3 (Jazz Festival - 2000 tickets)
INSERT INTO tickets (event_id, status, created_at)
SELECT 3, 'AVAILABLE', CURRENT_TIMESTAMP(6)
FROM (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
     (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
     (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t3,
     (SELECT 0 UNION SELECT 1) t4
LIMIT 2000;

-- Generate tickets for Event 4 (Comedy Night - 200 tickets)
INSERT INTO tickets (event_id, status, created_at)
SELECT 4, 'AVAILABLE', CURRENT_TIMESTAMP(6)
FROM (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
     (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2
LIMIT 200;

-- Verify data
SELECT 
    e.id,
    e.name,
    e.total_tickets,
    COUNT(t.id) as actual_tickets,
    SUM(CASE WHEN t.status = 'AVAILABLE' THEN 1 ELSE 0 END) as available_tickets
FROM events e
LEFT JOIN tickets t ON e.id = t.event_id
GROUP BY e.id, e.name, e.total_tickets;
