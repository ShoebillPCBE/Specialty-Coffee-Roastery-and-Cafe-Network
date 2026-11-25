package ro.upt.cafe.mcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Inventory batch as returned by Server B.
 * Accepts both formats used in the project:
 * - Member 1 (command-service IngredientBatch / BatchStockUpdatedEvent):
 *   category, name, remainingQuantity, roastOrOpenDate, expiryDate
 * - Member 2 (query-service BatchRegisteredEvent):
 *   ingredientCategory, ingredientName, quantity, roastDate, openedAt, expiryDate (ISO strings)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BatchView(String batchId,
                        String cafeId,
                        String name,
                        String category,
                        double remainingQuantity,
                        String unit,
                        LocalDate roastOrOpenDate,
                        LocalDate expiryDate) {

    @JsonCreator
    public static BatchView fromJson(@JsonProperty("batchId") String batchId,
                                     @JsonProperty("cafeId") String cafeId,
                                     @JsonProperty("name") String name,
                                     @JsonProperty("ingredientName") String ingredientName,
                                     @JsonProperty("category") String category,
                                     @JsonProperty("ingredientCategory") String ingredientCategory,
                                     @JsonProperty("remainingQuantity") Double remainingQuantity,
                                     @JsonProperty("quantity") Double quantity,
                                     @JsonProperty("unit") String unit,
                                     @JsonProperty("roastOrOpenDate") String roastOrOpenDate,
                                     @JsonProperty("roastDate") String roastDate,
                                     @JsonProperty("openedAt") String openedAt,
                                     @JsonProperty("expiryDate") String expiryDate) {
        String cat = firstNonBlank(category, ingredientCategory);
        // Matcha / milk age is counted from opening, coffee from roasting.
        boolean countsFromOpening = cat != null && !cat.toUpperCase(Locale.ROOT).endsWith("_BEAN");
        String date = firstNonBlank(roastOrOpenDate,
                countsFromOpening ? firstNonBlank(openedAt, roastDate) : firstNonBlank(roastDate, openedAt));
        double qty = remainingQuantity != null ? remainingQuantity : quantity != null ? quantity : 0;
        return new BatchView(batchId, cafeId, firstNonBlank(name, ingredientName), cat, qty, unit,
                parseDate(date), parseDate(expiryDate));
    }

    /** Accepts "2026-11-20", "2026-11-20T08:00:00" or "2026-11-20T08:00:00Z". */
    public static LocalDate parseDate(String raw) {
        if (raw == null || raw.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(raw.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}
