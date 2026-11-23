package ro.upt.cafe.command.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.upt.cafe.command.service.OrderCommandService;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderCommandService orderService;

    public OrderController(OrderCommandService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<String> placeOrder(
            @RequestParam String cafeId,
            @RequestParam String menuItemId,
            @RequestParam(defaultValue = "1") int quantity) {
        
        String orderId = orderService.placeOrder(cafeId, menuItemId, quantity);
        return ResponseEntity.ok("Order placed successfully. ID: " + orderId);
    }
}