package com.project.Registry_Service.Services;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RestartCounterService
{
    private static final Duration Window = Duration.ofMinutes(5);
    private final StringRedisTemplate redisTemplate;
    public RestartCounterService(StringRedisTemplate redisTemplate)
    {
        this.redisTemplate=redisTemplate;
    }

    public int increment(Long instanceId)
    {
        String key="restart:count:" + instanceId;
        Long count=redisTemplate.opsForValue().increment(key);
        if(count!= null && count ==1)
        {
            redisTemplate.expire(key,Window);
        }
        return count == null ? 0:count.intValue();
    }
}
