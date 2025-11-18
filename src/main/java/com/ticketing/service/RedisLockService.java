package com.ticketing.service;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class RedisLockService implements DistributedLockService {
    
    private static final Logger log = LoggerFactory.getLogger(RedisLockService.class);
    
    private final RedissonClient redissonClient;
    
    public RedisLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }
    
    @Override
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, 
                                 TimeUnit timeUnit, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            log.debug("Attempting to acquire Redis distributed lock for key: {}", lockKey);
            
            if (lock.tryLock(waitTime, leaseTime, timeUnit)) {
                try {
                    log.debug("Redis lock acquired for key: {}", lockKey);
                    return action.get();
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                        log.debug("Redis lock released for key: {}", lockKey);
                    }
                }
            } else {
                log.warn("Failed to acquire Redis lock for key: {} within {} {}", 
                        lockKey, waitTime, timeUnit);
                throw new RuntimeException("Could not acquire distributed lock for: " + lockKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Redis lock acquisition interrupted for key: {}", lockKey, e);
            throw new RuntimeException("Lock acquisition interrupted", e);
        }
    }
}

