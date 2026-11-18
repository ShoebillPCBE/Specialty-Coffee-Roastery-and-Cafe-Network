package ro.upt.cafe.mcp.model;

public record AtRiskBatch(String batchId,
                          String name,
                          String category,
                          double remainingQuantity,
                          String unit,
                          Long ageDays,
                          Long hoursUntilStaleOrExpired,
                          RiskType riskType,
                          Severity severity,
                          String note) {

    public enum RiskType { STALE_COFFEE, AGING_COFFEE, OXIDIZING_MATCHA, MATCHA_NEAR_OXIDATION, MILK_EXPIRING, EXPIRED, EXPIRING }

    public enum Severity { CRITICAL, HIGH, MEDIUM }
}
