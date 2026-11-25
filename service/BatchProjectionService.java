
package com.beandrop.query.service;

import com.beandrop.query.event.BatchRegisteredEvent;
import com.beandrop.query.repository.BatchRedisRepository;

import org.springframework.stereotype.Service;

@Service
public class BatchProjectionService {

    private final BatchRedisRepository repository;

    public BatchProjectionService(
            BatchRedisRepository repository
    ) {
        this.repository = repository;
    }

    public void registerBatch(BatchRegisteredEvent event) {

        repository.save(event);

        System.out.println(
                "Batch saved in Redis: " + event.batchId()
        );
    }
}
