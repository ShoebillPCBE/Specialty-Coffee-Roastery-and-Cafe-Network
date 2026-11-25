package ro.upt.cafe.command.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.upt.cafe.command.domain.MenuItem;
import ro.upt.cafe.command.events.OrderPlacedEvent;
import ro.upt.cafe.command.repository.MenuItemRepository;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrderCommandService {

    private final MenuItemRepository menuRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderCommandService(MenuItemRepository menuRepository, KafkaTemplate<String, Object> kafkaTemplate) {
        this.menuRepository = menuRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public String placeOrder(String cafeId, String menuItemId, int quantityCups) {
        // 1. Validate the menu item exists
        MenuItem item = menuRepository.findById(menuItemId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found: " + menuItemId));

        // Note: For now, we are skipping the recipe line deductions (which require the BatchRepository) 
        // to ensure the basic Kafka flow works. We will add full recipe deduction next!

        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);

        // 2. Publish order event to Kafka
        OrderPlacedEvent event = new OrderPlacedEvent(
                orderId,
                cafeId,
                item.getItemId(),
                item.getName(),
                item.getBeverageGroup(),
                quantityCups,
                Instant.now()
        );
        
        kafkaTemplate.send("cafe.order.events", cafeId, event);

        return orderId;
    }
}