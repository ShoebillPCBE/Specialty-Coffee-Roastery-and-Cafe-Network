
package com.beandrop.query.controller;

import com.beandrop.query.event.BatchRegisteredEvent;
import com.beandrop.query.repository.BatchRedisRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {

    private final BatchRedisRepository repository;

    public BatchController(BatchRedisRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{batchId}")
    public ResponseEntity<BatchRegisteredEvent> getBatch(
            @PathVariable String batchId
    ) {

        return repository.findById(batchId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
