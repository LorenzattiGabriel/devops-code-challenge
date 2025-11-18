package com.ticketing.config;

import com.ticketing.service.DistributedLockService;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test configuration for integration tests.
 * Mocks Redis-related beans to avoid needing actual Redis server during tests.
 */
@TestConfiguration
@Profile("test")
public class TestConfig {

    @Bean
    @Primary
    public RedissonClient redissonClient() {
        return mock(RedissonClient.class);
    }

    @Bean
    @Primary
    public DistributedLockService distributedLockService() {
        // Return a simple implementation that just executes the action without actual locking
        return new DistributedLockService() {
            @Override
            public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> action) {
                // For tests, just execute the action directly without distributed locking
                // This is safe because tests are single-threaded (except for the concurrent test)
                return action.get();
            }
        };
    }
}

