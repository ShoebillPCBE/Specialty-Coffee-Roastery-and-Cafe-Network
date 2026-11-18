package ro.upt.cafe.mcp.model;

import java.util.List;

/** Body POSTed to Server A to create a PENDING happy-hour bundle. */
public record FlashPromoRequest(String cafeId,
                                List<String> menuItemIds,
                                int discountPct,
                                int durationHours,
                                String reason,
                                String proposedBy) {}
