
package com.beandrop.query.repository;

import com.beandrop.query.event.BatchRegisteredEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class BatchRedisRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public BatchRedisRepository(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(BatchRegisteredEvent event) {
        String key = "batch:" + event.batchId();
        String json = objectMapper.writeValueAsString(event);
        redisTemplate.opsForValue().set(key, json);
    }

    public Optional<BatchRegisteredEvent> findById(String batchId) {
        String key = "batch:" + batchId;
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) {
            return Optional.empty();
        }

        BatchRegisteredEvent event =
                objectMapper.readValue(json, BatchRegisteredEvent.class);

        return Optional.of(event);
    }
}
