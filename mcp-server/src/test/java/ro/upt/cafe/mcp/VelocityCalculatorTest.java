package ro.upt.cafe.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import ro.upt.cafe.mcp.config.CafeProperties;
import ro.upt.cafe.mcp.model.DrinkVelocity;
import ro.upt.cafe.mcp.model.SalesRecord;
import ro.upt.cafe.mcp.model.SalesVelocityReport;
import ro.upt.cafe.mcp.tools.VelocityCalculator;

class VelocityCalculatorTest {

    final VelocityCalculator calculator = new VelocityCalculator(
            new CafeProperties(null, null, new CafeProperties.Freshness(21, 25, 48, 8)));

    @Test
    void aggregatesPerDrinkFamilyAndFlagsSlowAndAcceleratingDrinks() {
        LocalDate d1 = LocalDate.of(2026, 11, 16);
        LocalDate d2 = LocalDate.of(2026, 11, 17);
        List<SalesRecord> rows = List.of(
                new SalesRecord(d1, "Espresso", 40), new SalesRecord(d2, "Flat White", 40),
                new SalesRecord(d1, "Iced Matcha", 10), new SalesRecord(d2, "Matcha Latte", 30),
                new SalesRecord(d1, "Decaf", 3), new SalesRecord(d2, "decaf", 2),
                new SalesRecord(d2, "Hot Chocolate", 4));

        SalesVelocityReport report = calculator.compute("CAFE-01", 2, rows);

        assertThat(report.totalCups()).isEqualTo(129);
        assertThat(report.drinks()).extracting(DrinkVelocity::beverageGroup)
                .containsExactlyInAnyOrder("ESPRESSO", "DECAF", "MATCHA", "TEA", "HOT_CHOCOLATE");
        assertThat(find(report, "DECAF").status()).isEqualTo(DrinkVelocity.Status.SLOW_MOVING);
        assertThat(find(report, "TEA").totalCups()).isZero();
        assertThat(find(report, "MATCHA").status()).isEqualTo(DrinkVelocity.Status.ACCELERATING);
        assertThat(find(report, "ESPRESSO").status()).isEqualTo(DrinkVelocity.Status.STEADY);
    }

    private static DrinkVelocity find(SalesVelocityReport r, String group) {
        return r.drinks().stream().filter(d -> d.beverageGroup().equals(group)).findFirst().orElseThrow();
    }
}
