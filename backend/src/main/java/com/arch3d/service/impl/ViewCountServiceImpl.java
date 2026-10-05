package com.arch3d.service.impl;

import com.arch3d.service.ViewCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCountServiceImpl implements ViewCountService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String VIEW_COUNT_PREFIX = "view:";
    private static final Duration VIEW_COUNT_EXPIRE = Duration.ofHours(24);

    @Override
    public boolean canCountView(String key) {
        String redisKey = VIEW_COUNT_PREFIX + key;
        Boolean hasKey = redisTemplate.hasKey(redisKey);
        return !Boolean.TRUE.equals(hasKey);
    }

    @Override
    public void recordView(String key) {
        String redisKey = VIEW_COUNT_PREFIX + key;
        redisTemplate.opsForValue().set(redisKey, "1", VIEW_COUNT_EXPIRE);
        log.debug("Recorded view for key: {}", key);
    }

    @Override
    public boolean hasViewed(String key) {
        String redisKey = VIEW_COUNT_PREFIX + key;
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }
}
