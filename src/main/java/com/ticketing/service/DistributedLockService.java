package com.ticketing.service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Service for distributed locking across multiple instances.
 * Provides abstraction over different locking mechanisms (Redis, Database, etc.)
 */
public interface DistributedLockService {

    <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, 
                          TimeUnit timeUnit, Supplier<T> action);
}

