package ro.upt.cafe.mcp.tools;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import ro.upt.cafe.mcp.config.CafeProperties;
import ro.upt.cafe.mcp.model.DrinkVelocity;
import ro.upt.cafe.mcp.model.SalesRecord;
import ro.upt.cafe.mcp.model.SalesVelocityReport;

/** Aggregates daily cup sales per drink family and flags slow movers / accelerating sellers. */
@Component
public class VelocityCalculator {

    private final double slowCupsPerDay;

    public VelocityCalculator(CafeProperties props) {
        this.slowCupsPerDay = props.freshness().slowMovingCupsPerDay();
    }

    public SalesVelocityReport compute(String cafeId, int days, List<SalesRecord> rows) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        BeverageGroups.ALL.forEach(g -> totals.put(g, 0));
        LocalDate lastDay = rows.stream().map(SalesRecord::date).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        Map<String, Integer> lastDayCups = new LinkedHashMap<>();

        for (SalesRecord r : rows) {
            String group = BeverageGroups.normalize(r.beverageGroup());
            totals.merge(group, r.cups(), Integer::sum);
            if (lastDay != null && lastDay.equals(r.date())) {
                lastDayCups.merge(group, r.cups(), Integer::sum);
            }
        }

        int grand = totals.values().stream().mapToInt(Integer::intValue).sum();
        List<DrinkVelocity> drinks = new ArrayList<>();
        totals.forEach((group, total) -> {
            double avg = round(total / (double) Math.max(days, 1));
            int last = lastDayCups.getOrDefault(group, 0);
            double trend = avg == 0 ? 0 : round((last - avg) / avg * 100);
            double share = grand == 0 ? 0 : round(total * 100.0 / grand);
            DrinkVelocity.Status status = avg < slowCupsPerDay ? DrinkVelocity.Status.SLOW_MOVING
                    : trend >= 30 ? DrinkVelocity.Status.ACCELERATING
                    : DrinkVelocity.Status.STEADY;
            drinks.add(new DrinkVelocity(group, total, avg, last, trend, share, status));
        });
        drinks.sort(Comparator.comparingDouble(DrinkVelocity::avgCupsPerDay));
        return new SalesVelocityReport(cafeId, days, grand, drinks);
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
