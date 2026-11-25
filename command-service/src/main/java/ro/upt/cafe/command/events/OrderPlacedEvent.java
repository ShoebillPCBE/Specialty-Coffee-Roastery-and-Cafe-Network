package ro.upt.cafe.command.events;

import java.time.Instant;

public record OrderPlacedEvent(
    String orderId,
    String cafeId,
    String menuItemId,
    String menuItemName,
    String beverageGroup,
    int cupsSold,
    Instant timestamp
) {}