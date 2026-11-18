package ro.upt.cafe.mcp.model;

public record DrinkVelocity(String beverageGroup,
                            int totalCups,
                            double avgCupsPerDay,
                            int lastDayCups,
                            double trendPct,
                            double sharePct,
                            Status status) {

    /** SLOW_MOVING = candidate for a promo; ACCELERATING = may stock out before rush hour. */
    public enum Status { SLOW_MOVING, STEADY, ACCELERATING }
}
