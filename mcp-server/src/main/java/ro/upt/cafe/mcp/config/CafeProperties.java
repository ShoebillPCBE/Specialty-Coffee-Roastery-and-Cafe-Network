package ro.upt.cafe.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cafe")
public record CafeProperties(ServerA serverA, ServerB serverB, Freshness freshness) {

    public record ServerA(String baseUrl, String flashPromoPath, String transferPath) {}

    public record ServerB(String baseUrl, String batchesPath, String salesPath) {}

    public record Freshness(int coffeeStaleAfterDays,
                            int matchaOxidizedAfterDays,
                            int milkExpiryWindowHours,
                            double slowMovingCupsPerDay) {}
}
