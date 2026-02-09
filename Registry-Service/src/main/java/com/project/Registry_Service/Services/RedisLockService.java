package com.project.Registry_Service.Services;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Service
public class RedisLockService
{
    private final StringRedisTemplate redisTemplate;
    public RedisLockService(StringRedisTemplate redisTemplate)
    {
        this.redisTemplate=redisTemplate;
    }
    public boolean acquireLock(String key,String ownerId,long ttlSeconds)
    {
        Boolean success=redisTemplate.opsForValue().setIfAbsent(key,ownerId, Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(success);
    }

    public void releaseLock(String key,String ownerId)
    {
        String lua= """
                if redis.call('get',KEYS[1]) == ARGV[1] then
                    return redis.call('del',KEYS[1])
                else
                    return 0
                end               
                """;
        redisTemplate.execute(new DefaultRedisScript<>(lua,Long.class), Collections.singletonList(key),ownerId);
    }
}
