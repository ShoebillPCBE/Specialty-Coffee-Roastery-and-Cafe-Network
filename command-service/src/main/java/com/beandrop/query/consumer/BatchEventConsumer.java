
package com.beandrop.query.consumer;

import com.beandrop.query.config.RabbitMQConfig;
import com.beandrop.query.event.BatchRegisteredEvent;
import com.beandrop.query.service.BatchProjectionService;

import tools.jackson.databind.ObjectMapper;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class BatchEventConsumer {

    private final ObjectMapper objectMapper;
    private final BatchProjectionService projectionService;

    public BatchEventConsumer(
            ObjectMapper objectMapper,
            BatchProjectionService projectionService
    ) {
        this.objectMapper = objectMapper;
        this.projectionService = projectionService;
    }

    @RabbitListener(
            queues = RabbitMQConfig.BATCH_REGISTERED_QUEUE
    )
    public void receiveBatchRegisteredEvent(Message message) {

        String json = new String(
                message.getBody(),
                StandardCharsets.UTF_8
        );

        BatchRegisteredEvent event =
                objectMapper.readValue(
                        json,
                        BatchRegisteredEvent.class
                );

        System.out.println(
                "Received batch event: " + event.batchId()
        );

        projectionService.registerBatch(event);
    }
}
