package ro.upt.cafe.mcp.model;

import java.util.List;

public record SalesVelocityReport(String cafeId, int days, int totalCups, List<DrinkVelocity> drinks) {}
