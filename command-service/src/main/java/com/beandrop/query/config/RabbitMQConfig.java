
package com.beandrop.query.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String BATCH_REGISTERED_QUEUE =
            "batch.registered";

    @Bean
    public Queue batchRegisteredQueue() {
        return new Queue(BATCH_REGISTERED_QUEUE, true);
    }
}
