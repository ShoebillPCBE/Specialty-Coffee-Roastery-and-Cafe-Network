package ro.upt.cafe.command.events;

import java.time.Instant;
import java.time.LocalDate;

public record BatchStockUpdatedEvent(
    String batchId,
    String cafeId,
    String category,
    double remainingQuantity,
    LocalDate roastOrOpenDate,
    LocalDate expiryDate,
    Instant timestamp
) {}