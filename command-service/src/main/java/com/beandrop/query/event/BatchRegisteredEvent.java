
package com.beandrop.query.event;

public record BatchRegisteredEvent(
        String eventId,
        String eventType,
        String timestamp,
        String batchId,
        String roasteryId,
        String ingredientCategory,
        String ingredientName,
        String origin,
        String process,
        String roastDate,
        double quantity,
        String unit,
        String cafeId,
        String openedAt,
        String expiryDate
) {}
