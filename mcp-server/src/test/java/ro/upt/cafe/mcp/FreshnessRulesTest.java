package ro.upt.cafe.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import ro.upt.cafe.mcp.config.CafeProperties;
import ro.upt.cafe.mcp.model.AtRiskBatch;
import ro.upt.cafe.mcp.model.AtRiskBatch.RiskType;
import ro.upt.cafe.mcp.model.BatchView;
import ro.upt.cafe.mcp.tools.FreshnessRules;

class FreshnessRulesTest {

    static final CafeProperties PROPS = new CafeProperties(null, null,
            new CafeProperties.Freshness(21, 25, 48, 8));
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 7, 0);
    static final LocalDate TODAY = NOW.toLocalDate();

    final FreshnessRules rules = new FreshnessRules(PROPS);

    @Test
    void flagsStaleCoffeeOxidizedMatchaAndExpiringMilk() {
        List<BatchView> batches = List.of(
                batch("B-COF-OLD", "DECAF_BEAN", 1200, TODAY.minusDays(23), null),
                batch("B-COF-FRESH", "ESPRESSO_BEAN", 2000, TODAY.minusDays(9), null),
                batch("B-MAT-4", "MATCHA_TIN", 80, TODAY.minusDays(27), null),
                batch("B-OAT", "OAT_MILK", 1200, TODAY.minusDays(3), TODAY.plusDays(1)),
                batch("B-DAIRY-OK", "DAIRY_MILK", 5000, TODAY, TODAY.plusDays(6)));

        List<AtRiskBatch> result = rules.evaluate(batches, 48, NOW);

        assertThat(result).extracting(AtRiskBatch::batchId)
                .containsExactlyInAnyOrder("B-COF-OLD", "B-MAT-4", "B-OAT");
        assertThat(result).filteredOn(b -> b.batchId().equals("B-COF-OLD"))
                .extracting(AtRiskBatch::riskType).containsExactly(RiskType.STALE_COFFEE);
        assertThat(result).filteredOn(b -> b.batchId().equals("B-MAT-4"))
                .extracting(AtRiskBatch::riskType).containsExactly(RiskType.OXIDIZING_MATCHA);
        assertThat(result).filteredOn(b -> b.batchId().equals("B-OAT"))
                .extracting(AtRiskBatch::riskType).containsExactly(RiskType.MILK_EXPIRING);
    }

    @Test
    void reportsBatchesThatCrossTheLimitInsideTheWindow() {
        List<BatchView> batches = List.of(batch("B-COF-20", "FILTER_BEAN", 500, TODAY.minusDays(20), null));

        assertThat(rules.evaluate(batches, 48, NOW))
                .singleElement()
                .extracting(AtRiskBatch::riskType).isEqualTo(RiskType.AGING_COFFEE);
        assertThat(rules.evaluate(batches, 6, NOW)).isEmpty();
    }

    @Test
    void ignoresEmptyBatches() {
        assertThat(rules.evaluate(List.of(batch("X", "MATCHA_TIN", 0, TODAY.minusDays(40), null)), 48, NOW)).isEmpty();
    }

    private static BatchView batch(String id, String category, double qty, LocalDate opened, LocalDate expiry) {
        return new BatchView(id, "CAFE-01", id, category, qty, "g", opened, expiry);
    }
}
