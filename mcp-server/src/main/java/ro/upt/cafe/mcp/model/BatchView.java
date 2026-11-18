package ro.upt.cafe.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

/** Inventory batch as returned by Server B (mirrors command-service IngredientBatch). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BatchView(String batchId,
                        String cafeId,
                        String name,
                        String category,
                        double remainingQuantity,
                        String unit,
                        LocalDate roastOrOpenDate,
                        LocalDate expiryDate) {}
