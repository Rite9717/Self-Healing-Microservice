package com.project.Registry_Service.Services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributed locking service using Redis with in-memory fallback.
 * Falls back to ConcurrentHashMap if Redis is unavailable.
 */
@Slf4j
@Service
public class RedisLockService
{
    private final StringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<String, String> inMemoryLocks;
    private boolean redisAvailable = true;
    
    public RedisLockService(StringRedisTemplate redisTemplate)
    {
        this.redisTemplate = redisTemplate;
        this.inMemoryLocks = new ConcurrentHashMap<>();
    }
    
    /**
     * Acquire a distributed lock
     * 
     * @param key the lock key
     * @param ownerId the owner identifier
     * @param ttlMs time-to-live in milliseconds
     * @return true if lock acquired, false otherwise
     */
    public boolean acquireLock(String key, String ownerId, long ttlMs)
    {
        try {
            Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, ownerId, Duration.ofMillis(ttlMs));
            
            if (!redisAvailable) {
                log.info("Redis connection restored");
                redisAvailable = true;
            }
            
            return Boolean.TRUE.equals(success);
            
        } catch (Exception e) {
            if (redisAvailable) {
                log.warn("Redis unavailable, falling back to in-memory locking: {}", e.getMessage());
                redisAvailable = false;
            }
            
            return acquireLockInMemory(key, ownerId);
        }
    }

    /**
     * Release a distributed lock
     * 
     * @param key the lock key
     * @param ownerId the owner identifier
     */
    public void releaseLock(String key, String ownerId)
    {
        try {
            String lua = """
                    if redis.call('get',KEYS[1]) == ARGV[1] then
                        return redis.call('del',KEYS[1])
                    else
                        return 0
                    end               
                    """;
            redisTemplate.execute(
                new DefaultRedisScript<>(lua, Long.class), 
                Collections.singletonList(key), 
                ownerId
            );
            
        } catch (Exception e) {
            log.debug("Redis unavailable during lock release, using in-memory fallback");
            releaseLockInMemory(key, ownerId);
        }
    }

    /**
     * Acquire lock using in-memory ConcurrentHashMap
     */
    private boolean acquireLockInMemory(String key, String ownerId) {
        String existingOwner = inMemoryLocks.putIfAbsent(key, ownerId);
        return existingOwner == null;
    }

    /**
     * Release lock from in-memory ConcurrentHashMap
     */
    private void releaseLockInMemory(String key, String ownerId) {
        inMemoryLocks.remove(key, ownerId);
    }
}
